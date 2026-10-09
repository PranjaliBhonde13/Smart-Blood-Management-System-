import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useStore } from 'react-redux';
import {
  Siren, UserRound, Droplet, Clock, MapPin, FileText, ClipboardCheck, BadgeCheck, AlertTriangle,
  Navigation, ChevronLeft, ChevronRight, CheckCircle2, Info,
} from 'lucide-react';
import { useCreateRequestMutation } from '../requestsApi';
import { useHospitalOptionsQuery, useMyPatientProfileQuery } from '../../profile/patient/patientApi';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { uploadWithProgress } from '../../../lib/privateFiles';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import RadioGroup from '../../../components/ui/RadioGroup';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import LicenseUploader from '../../profile/bloodbank/LicenseUploader';
import { formatDateTime } from '../../../lib/format';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
const COMPONENTS = [
  { value: 'WHOLE_BLOOD', label: 'Whole blood' },
  { value: 'PACKED_RED_CELLS', label: 'Packed red blood cells (PRBC)' },
  { value: 'PLATELETS', label: 'Platelets' },
  { value: 'FRESH_FROZEN_PLASMA', label: 'Fresh frozen plasma (FFP)' },
  { value: 'CRYOPRECIPITATE', label: 'Cryoprecipitate' },
  { value: 'NOT_SURE', label: 'Not sure — the hospital will advise' },
];
const COMPONENT_LABEL = Object.fromEntries(COMPONENTS.map((c) => [c.value, c.label]));
const URGENCIES = [
  { value: 'LOW', label: 'Low', hint: 'Planned / routine' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Needed in a few days' },
  { value: 'HIGH', label: 'High', hint: 'Needed today' },
  { value: 'CRITICAL', label: 'Critical', hint: 'Life-threatening — needed now' },
];
const STEPS = [
  { key: 'patient', title: 'Patient', icon: UserRound },
  { key: 'blood', title: 'Blood needed', icon: Droplet },
  { key: 'when', title: 'Quantity & timing', icon: Clock },
  { key: 'where', title: 'Hospital & location', icon: MapPin },
  { key: 'document', title: 'Supporting document', icon: FileText },
  { key: 'review', title: 'Review & submit', icon: ClipboardCheck },
];
/** Which step owns each field, so server errors jump to the right step. */
const FIELD_STEP = {
  requestFor: 0, recipientName: 0, recipientAge: 0, recipientRelationship: 0,
  bloodGroup: 1, component: 1, unitsRequired: 2, requiredBy: 2, urgency: 2,
  preferredHospitalId: 3, locationNotes: 3, latitude: 3, longitude: 3,
};

function localIso(date) {
  const d = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return d.toISOString().slice(0, 16);
}
const inHours = (h) => localIso(new Date(Date.now() + h * 3600_000));

/**
 * Step-by-step blood request. For "Myself" the blood group is read from the
 * patient's profile on the server (and re-derived by the server on submit);
 * for "Another patient" the requester enters it and a hospital must confirm it.
 * The group is what is requested — compatibility and crossmatch are decided by
 * blood-bank staff, so this page never suggests alternative groups.
 */
export default function CreateRequestPage() {
  const navigate = useNavigate();
  const store = useStore();
  const geo = useGeolocation();
  const [createRequest, { isLoading: creating }] = useCreateRequestMutation();
  // Always refetch: an admin may have verified (or a change re-opened) the blood group since last visit.
  const { data: profileData, isLoading: profileLoading, error: profileError } =
    useMyPatientProfileQuery(undefined, { refetchOnMountOrArgChange: true, refetchOnFocus: true });
  const { data: hospitalsData } = useHospitalOptionsQuery();
  const profile = profileData?.data;
  const hospitals = hospitalsData?.data || [];

  const [step, setStep] = useState(0);
  const [form, setForm] = useState({
    requestFor: 'SELF', recipientName: '', recipientAge: '', recipientRelationship: '',
    bloodGroup: '', component: '', unitsRequired: '1', requiredBy: inHours(6), urgency: 'HIGH',
    preferredHospitalId: '', locationNotes: '', latitude: '', longitude: '',
  });
  const [doc, setDoc] = useState(null);
  const [docProgress, setDocProgress] = useState(null);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [created, setCreated] = useState(null);
  const [docUploadError, setDocUploadError] = useState('');

  const isSelf = form.requestFor === 'SELF';
  const selfGroup = profile?.bloodGroup || '';
  const selfVerified = !!profile?.bloodGroupVerified;
  const effectiveGroup = isSelf ? selfGroup : form.bloodGroup;
  const needsConfirmation = !isSelf || !selfVerified;
  const hospital = hospitals.find((h) => String(h.id) === String(form.preferredHospitalId));

  const set = (k, v) => { setForm((f) => ({ ...f, [k]: v })); setErrors((e) => { const o = { ...e }; delete o[k]; return o; }); };
  const bind = (k) => ({ id: `req-${k}`, value: form[k], error: errors[k], onChange: (e) => set(k, e.target.value) });

  const validateStep = (i) => {
    const e = {};
    if (i === 0 && !isSelf) {
      if (!form.recipientName.trim()) e.recipientName = "Enter the patient's name";
      if (form.recipientAge === '' || Number(form.recipientAge) < 0 || Number(form.recipientAge) > 120) e.recipientAge = 'Enter an age from 0 to 120';
    }
    if (i === 1) {
      if (isSelf && !selfGroup) e.bloodGroup = 'Add your blood group in your profile first';
      if (!isSelf && !form.bloodGroup) e.bloodGroup = "Choose the patient's blood group";
      if (!form.component) e.component = 'Choose the component, or "Not sure"';
    }
    if (i === 2) {
      const u = Number(form.unitsRequired);
      if (!Number.isInteger(u) || u < 1 || u > 20) e.unitsRequired = 'Enter 1 to 20 units';
      if (!form.requiredBy) e.requiredBy = 'Enter when the blood is required';
      else if (new Date(form.requiredBy) < new Date(Date.now() - 5 * 60_000)) e.requiredBy = 'Required-by time must be in the future';
      else if (new Date(form.requiredBy) > new Date(Date.now() + 90 * 86_400_000)) e.requiredBy = 'Must be within 90 days';
    }
    if (i === 3) {
      const lat = form.latitude.trim();
      const lng = form.longitude.trim();
      if (!!lat !== !!lng) e[lat ? 'longitude' : 'latitude'] = 'Enter both latitude and longitude, or neither';
      if (lat && (Number.isNaN(Number(lat)) || Math.abs(Number(lat)) > 90)) e.latitude = 'Latitude must be between -90 and 90';
      if (lng && (Number.isNaN(Number(lng)) || Math.abs(Number(lng)) > 180)) e.longitude = 'Longitude must be between -180 and 180';
    }
    return e;
  };

  const next = () => {
    const e = validateStep(step);
    setErrors(e);
    if (Object.keys(e).length) { notify.error('Please fix the highlighted fields'); return; }
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };
  const back = () => setStep((s) => Math.max(s - 1, 0));

  const locate = async () => {
    try {
      const { latitude, longitude } = await geo.request();
      setForm((f) => ({ ...f, latitude: latitude.toFixed(7), longitude: longitude.toFixed(7) }));
    } catch { /* message via geo.error */ }
  };

  const submit = async () => {
    for (let i = 0; i < STEPS.length - 1; i += 1) {
      const e = validateStep(i);
      if (Object.keys(e).length) { setErrors(e); setStep(i); return; }
    }
    setServerError('');
    const body = {
      requestFor: form.requestFor,
      // For SELF the server ignores-or-checks this against the profile; it never trusts it blindly.
      bloodGroup: isSelf ? null : form.bloodGroup,
      component: form.component,
      unitsRequired: Number(form.unitsRequired),
      urgency: form.urgency,
      requiredBy: `${form.requiredBy}:00`,
      recipientName: isSelf ? null : form.recipientName.trim(),
      recipientAge: isSelf ? null : Number(form.recipientAge),
      recipientRelationship: isSelf ? null : form.recipientRelationship.trim() || null,
      preferredHospitalId: form.preferredHospitalId ? Number(form.preferredHospitalId) : null,
      locationNotes: form.locationNotes.trim() || null,
      latitude: form.latitude.trim() ? Number(form.latitude) : null,
      longitude: form.longitude.trim() ? Number(form.longitude) : null,
    };
    let res;
    try {
      res = (await createRequest(body).unwrap()).data;
    } catch (err) {
      const { message, fieldErrors } = parseApiError(err);
      setServerError(message);
      setErrors(fieldErrors);
      const steps = Object.keys(fieldErrors).map((k) => FIELD_STEP[k]).filter((s) => s != null);
      if (steps.length) setStep(Math.min(...steps));
      notify.error(message);
      return;
    }
    if (doc) {
      setDocProgress(0);
      try {
        await uploadWithProgress(`/blood-requests/${res.id}/report`, doc, store.getState().auth?.accessToken, setDocProgress);
      } catch (e) {
        setDocUploadError(`Request #${res.id} was created, but the document upload failed: ${e.message}. You can attach it from My Requests.`);
      } finally {
        setDocProgress(null);
      }
    }
    setCreated(res);
    notify.success(`Request #${res.id} submitted`);
  };

  // ---------- success ----------
  if (created) {
    return (
      <div className="mx-auto max-w-2xl">
        <Card className="text-center">
          <CheckCircle2 className="mx-auto h-14 w-14 text-success" aria-hidden="true" />
          <h1 className="mt-3 text-2xl font-extrabold text-slate-900 dark:text-white">Request #{created.id} submitted</h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
            {created.unitsRequired} unit(s) of {COMPONENT_LABEL[created.component]?.toLowerCase()} · {created.bloodGroup} for {created.recipientName}
          </p>
          <div className="mx-auto mt-4 max-w-md text-left">
            <Alert type="info" title="What happens next">
              A hospital reviews the request{created.bloodGroupApprovable ? '' : ' and confirms the blood group'} before donors and blood banks are alerted.
              You'll be notified at every step.
            </Alert>
            {docUploadError && <Alert type="warning">{docUploadError}</Alert>}
          </div>
          <div className="mt-4 flex justify-center gap-3">
            <Button onClick={() => navigate('/patient/requests')}>View my requests</Button>
            <Button variant="secondary" onClick={() => { setCreated(null); setStep(0); setDoc(null); }}>New request</Button>
          </div>
        </Card>
      </div>
    );
  }

  const noProfile = profileError?.status === 404;

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon={Siren} breadcrumb="Home / Requests / New" title="New blood request"
                  subtitle="Answer a few questions — a hospital reviews every request before matching starts." />

      <div className="mb-6 flex items-center gap-3 overflow-hidden rounded-2xl bg-grad-brand p-4 text-white shadow-emergency">
        <span className="grid h-11 w-11 shrink-0 animate-pulse-ring place-items-center rounded-xl bg-white/20">
          <Siren className="h-6 w-6" aria-hidden="true" />
        </span>
        <div>
          <p className="font-bold">Life-threatening emergency? Call your local emergency number first.</p>
          <p className="text-sm text-white/85">Critical requests are flagged to hospitals immediately, but still need their review.</p>
        </div>
      </div>

      {/* step indicator */}
      <ol className="mb-6 grid grid-cols-3 gap-2 sm:grid-cols-6" aria-label="Request steps">
        {STEPS.map((s, i) => {
          const Icon = s.icon;
          const state = i < step ? 'done' : i === step ? 'current' : 'todo';
          return (
            <li key={s.key}>
              <button type="button" disabled={i > step} onClick={() => setStep(i)} aria-current={state === 'current' ? 'step' : undefined}
                      className={'flex w-full flex-col items-center gap-1 rounded-xl px-1 py-2 text-center text-[11px] font-semibold transition disabled:cursor-not-allowed ' +
                        (state === 'current' ? 'bg-brand-50 text-brand-700 ring-1 ring-brand-200 dark:bg-brand-900/30 dark:text-brand-200 dark:ring-brand-900'
                          : state === 'done' ? 'text-success hover:bg-slate-50 dark:hover:bg-slate-800' : 'text-slate-400')}>
                {state === 'done' ? <CheckCircle2 className="h-5 w-5" aria-hidden="true" /> : <Icon className="h-5 w-5" aria-hidden="true" />}
                <span><span className="sr-only">Step {i + 1}: </span>{s.title}</span>
              </button>
            </li>
          );
        })}
      </ol>

      <Alert type="error" title="Could not submit">{serverError}</Alert>

      <Card>
        <h2 className="mb-4 text-lg font-bold text-slate-900 dark:text-white">{step + 1}. {STEPS[step].title}</h2>

        {/* 1 — patient */}
        {step === 0 && (
          <div className="space-y-4">
            <RadioGroup id="req-requestFor" name="requestFor" legend="Who is this request for?" value={form.requestFor}
                        onChange={(v) => set('requestFor', v)} required
                        options={[
                          { value: 'SELF', label: 'Myself', hint: 'Uses the blood group saved in your profile.' },
                          { value: 'OTHER', label: 'Another patient', hint: 'A family member or someone you are helping.' },
                        ]} />
            {!isSelf && (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <TextField className="sm:col-span-2" label="Patient's full name *" maxLength={100} aria-required="true" {...bind('recipientName')} />
                <TextField label="Patient's age *" type="number" inputMode="numeric" min={0} max={120} aria-required="true" {...bind('recipientAge')} />
                <TextField label="Your relationship (optional)" maxLength={50} placeholder="e.g. Father, Friend" {...bind('recipientRelationship')} />
              </div>
            )}
          </div>
        )}

        {/* 2 — blood group + component */}
        {step === 1 && (
          <div className="space-y-5">
            {isSelf ? (
              profileLoading ? <Skeleton className="h-24 rounded-2xl" /> : noProfile || !selfGroup ? (
                <Alert type="warning" title="No blood group in your profile">
                  Add your blood group in <Link className="font-semibold underline" to="/patient/profile">My Profile</Link> first, or choose “Another patient” if the request is for someone else.
                </Alert>
              ) : (
                <div className={'flex items-center gap-4 rounded-2xl border p-4 ' + (selfVerified
                  ? 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30'
                  : 'border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30')}>
                  <span className="grid h-16 w-16 shrink-0 place-items-center rounded-2xl bg-white text-2xl font-extrabold text-brand-600 shadow-soft dark:bg-slate-900" aria-label={`Blood group ${selfGroup}`}>
                    {selfGroup}
                  </span>
                  <div className="min-w-0 text-sm">
                    <p className="font-semibold text-slate-900 dark:text-white">Your blood group (from your profile)</p>
                    {selfVerified ? (
                      <p className="mt-1 inline-flex items-center gap-1 font-medium text-success"><BadgeCheck className="h-4 w-4" aria-hidden="true" /> Verified by an administrator · read-only</p>
                    ) : (
                      <p className="mt-1 text-amber-800 dark:text-amber-200">
                        <AlertTriangle className="mr-1 inline h-4 w-4" aria-hidden="true" />
                        Not verified ({(profile?.bloodGroupVerification?.status || 'NOT_SUBMITTED').toLowerCase().replace('_', ' ')}).
                        You can still submit; the hospital must confirm the group before approval. Attach a lab report in the next steps, or
                        {' '}<Link className="font-semibold underline" to="/patient/profile">upload proof in your profile</Link>.
                      </p>
                    )}
                    <p className="mt-1 text-xs text-slate-500">To change it, update your profile. Requests for someone else use “Another patient”.</p>
                  </div>
                </div>
              )
            ) : null}
            {!isSelf && (
              <div>
                <Select id="req-bloodGroup" label="Patient's blood group *" value={form.bloodGroup} error={errors.bloodGroup}
                        onChange={(e) => set('bloodGroup', e.target.value)} aria-required="true">
                  <option value="">Select…</option>
                  {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
                </Select>
                <p className="mt-1.5 text-xs text-slate-500">Enter the group from the patient's lab report or hospital record. A hospital will confirm it before approval.</p>
              </div>
            )}
            {isSelf && errors.bloodGroup && <p className="text-xs font-medium text-brand-600" role="alert">{errors.bloodGroup}</p>}
            <RadioGroup id="req-component" name="component" legend="Component required *" value={form.component}
                        onChange={(v) => set('component', v)} error={errors.component} required options={COMPONENTS}
                        hint="Usually written on the doctor's requisition. If unsure, choose “Not sure”." />
            <p className="flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:bg-slate-800/60 dark:text-slate-300">
              <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              This is the group and component being requested. Compatibility, crossmatching and the final component are decided by qualified blood-bank staff.
            </p>
          </div>
        )}

        {/* 3 — units, required-by, urgency */}
        {step === 2 && (
          <div className="space-y-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <TextField label="Units required *" type="number" inputMode="numeric" min={1} max={20} aria-required="true"
                         hint="1–20 units, as advised by the doctor" {...bind('unitsRequired')} />
              <div>
                <TextField label="Required by *" type="datetime-local" min={localIso(new Date())} aria-required="true" {...bind('requiredBy')} />
                <div className="mt-2 flex flex-wrap gap-2">
                  {[['In 2 hours', 2], ['In 6 hours', 6], ['Tomorrow', 24], ['In 3 days', 72]].map(([label, h]) => (
                    <button key={label} type="button" onClick={() => set('requiredBy', inHours(h))}
                            className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300">{label}</button>
                  ))}
                </div>
              </div>
            </div>
            <RadioGroup id="req-urgency" name="urgency" legend="Urgency *" value={form.urgency} required columns={2}
                        onChange={(v) => set('urgency', v)} options={URGENCIES} error={errors.urgency} />
            {form.urgency === 'CRITICAL' && (
              <Alert type="warning">Critical requests are highlighted to hospitals, but a hospital or administrator still reviews them before donors are alerted.</Alert>
            )}
          </div>
        )}

        {/* 4 — hospital & location */}
        {step === 3 && (
          <div className="space-y-4">
            <Select id="req-preferredHospitalId" label="Preferred hospital (optional)" value={form.preferredHospitalId}
                    error={errors.preferredHospitalId} onChange={(e) => set('preferredHospitalId', e.target.value)}
                    hint="Where the patient is admitted or will receive the transfusion. That hospital is notified.">
              <option value="">No preference</option>
              {hospitals.map((h) => <option key={h.id} value={h.id}>{h.name}{h.city ? ` — ${h.city}` : ''}</option>)}
            </Select>
            <TextField label="Ward / delivery notes (optional)" maxLength={255} placeholder="e.g. ICU, bed 12"
                       hint="Visible to hospitals and administrators only" {...bind('locationNotes')} />
            <div className="rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-200">Location for nearby matching (optional)</span>
                <div className="flex gap-2">
                  {profile?.latitude != null && (
                    <Button type="button" size="sm" variant="ghost"
                            onClick={() => setForm((f) => ({ ...f, latitude: String(profile.latitude), longitude: String(profile.longitude) }))}>
                      Use profile location
                    </Button>
                  )}
                  <Button type="button" size="sm" variant="secondary" loading={geo.loading} onClick={locate}
                          leftIcon={<Navigation className="h-4 w-4" />}>Current location</Button>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <TextField label="Latitude" inputMode="decimal" {...bind('latitude')} />
                <TextField label="Longitude" inputMode="decimal" {...bind('longitude')} />
              </div>
              {geo.error && <p className="mt-2 text-xs text-amber-700 dark:text-amber-300">{geo.error} You can enter coordinates manually or leave them blank.</p>}
            </div>
          </div>
        )}

        {/* 5 — document */}
        {step === 4 && (
          <div className="space-y-3">
            {needsConfirmation ? (
              <Alert type="info" title="Recommended for this request">
                {isSelf ? 'Your blood group isn’t verified yet.' : 'The blood group was entered for another patient.'}{' '}
                Attach the doctor's requisition or a lab report showing the blood group so the hospital can confirm it quickly.
              </Alert>
            ) : (
              <p className="text-sm text-slate-600 dark:text-slate-300">Optional: attach the doctor's prescription or requisition form.</p>
            )}
            <LicenseUploader noun="prescription or supporting document" saved={null} staged={doc} onStage={setDoc}
                             onViewSaved={() => {}} progress={docProgress}
                             guidance="Doctor's requisition, prescription or lab report. Uploaded after the request is created." />
          </div>
        )}

        {/* 6 — review */}
        {step === 5 && (
          <div className="space-y-4">
            <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              {[
                ['For', isSelf ? `Myself (${profile?.fullName || 'you'})` : `${form.recipientName} · ${form.recipientAge} yrs${form.recipientRelationship ? ` · ${form.recipientRelationship}` : ''}`],
                ['Blood group', <span key="bg" className="inline-flex items-center gap-2 font-bold">{effectiveGroup}
                  {isSelf && selfVerified ? <Badge tone="success">Verified</Badge> : <Badge tone="warning">Needs hospital confirmation</Badge>}</span>],
                ['Component', COMPONENT_LABEL[form.component]],
                ['Units', form.unitsRequired],
                ['Required by', formatDateTime(form.requiredBy)],
                ['Urgency', URGENCIES.find((u) => u.value === form.urgency)?.label],
                ['Preferred hospital', hospital ? hospital.name : 'No preference'],
                ['Notes', form.locationNotes || '—'],
                ['Location', form.latitude ? `${form.latitude}, ${form.longitude}` : 'Not shared'],
                ['Document', doc ? doc.name : 'None attached'],
              ].map(([k, v]) => (
                <div key={k} className="border-b border-slate-100 pb-2 dark:border-slate-800">
                  <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{k}</dt>
                  <dd className="mt-0.5 text-slate-800 dark:text-slate-100">{v}</dd>
                </div>
              ))}
            </dl>
            <p className="text-xs text-slate-500">
              By submitting, you confirm these details are correct to the best of your knowledge. Donors and blood banks see the blood need and location — not the patient's name.
            </p>
          </div>
        )}

        <div className="mt-6 flex flex-wrap justify-between gap-3 border-t border-slate-100 pt-4 dark:border-slate-800">
          <Button type="button" variant="ghost" onClick={step === 0 ? () => navigate('/patient/requests') : back}
                  leftIcon={<ChevronLeft className="h-4 w-4" />} disabled={creating || docProgress != null}>
            {step === 0 ? 'Cancel' : 'Back'}
          </Button>
          {step < STEPS.length - 1 ? (
            <Button type="button" onClick={next} rightIcon={<ChevronRight className="h-4 w-4" />}
                    disabled={step === 1 && isSelf && (profileLoading || !selfGroup)}>Continue</Button>
          ) : (
            <Button type="button" size="lg" onClick={submit} loading={creating || docProgress != null}
                    leftIcon={<Siren className="h-4 w-4" />}>
              {docProgress != null ? `Uploading document ${docProgress}%` : 'Submit request'}
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}
