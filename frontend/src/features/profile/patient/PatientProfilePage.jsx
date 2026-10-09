import { useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useStore } from 'react-redux';
import {
  UserRound, MapPin, ShieldCheck, Phone, Navigation, Save, Lock, BadgeCheck, AlertTriangle, Mail,
} from 'lucide-react';
import { useAuth } from '../../../hooks/useAuth';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { apiSlice } from '../../../app/apiSlice';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { openPrivateFile, uploadWithProgress } from '../../../lib/privateFiles';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import RadioGroup from '../../../components/ui/RadioGroup';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import ProfileSummaryCard from '../ProfileSummaryCard';
import { useUnsavedChangesGuard } from '../bloodbank/useUnsavedChangesGuard';
import { useMyPatientDocumentsQuery, useMyPatientProfileQuery, useSavePatientProfileMutation } from './patientApi';
import {
  BLOOD_GROUPS, BLOOD_GROUP_DOC_TYPES, EMPTY_FORM, FIELD_ORDER, GENDERS, IDENTITY_TYPES, INDIAN_STATES,
  OVERALL_META, TRACK_META, completion, fieldId, toForm, toPayload, todayIso, validate,
} from './patientProfileModel';
import PatientDocumentCard from './PatientDocumentCard';

const GEO_ERRORS = {
  1: 'Location permission was denied. You can allow it in your browser settings, or enter coordinates manually (optional).',
  2: 'Your location is unavailable right now. Coordinates are optional — you can leave them blank.',
  3: 'Finding your location timed out. Try again, or leave the coordinates blank.',
};

function Label({ children, required, optional }) {
  return (
    <>
      {children}
      {required && <span className="ml-0.5 text-brand-600" aria-hidden="true">*</span>}
      {optional && <span className="ml-1 text-xs font-normal text-slate-400">(optional)</span>}
    </>
  );
}

function Section({ id, icon: Icon, title, description, children }) {
  return (
    <Card className="mb-6" role="region" aria-labelledby={`${id}-title`}>
      <div className="mb-5 flex items-start gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
          <Icon className="h-5 w-5" aria-hidden="true" />
        </span>
        <div>
          <h2 id={`${id}-title`} className="text-lg font-bold text-slate-900 dark:text-white">{title}</h2>
          {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
        </div>
      </div>
      {children}
    </Card>
  );
}

const grid = 'grid grid-cols-1 gap-4 md:grid-cols-2';
const full = 'md:col-span-2';

/**
 * Patient profile with identity and blood-group verification. All statuses are
 * read from the server; saving the profile never verifies anything.
 */
export default function PatientProfilePage() {
  const { user } = useAuth();
  const store = useStore();
  const dispatch = useDispatch();
  const geo = useGeolocation();
  const { data, isLoading, isError, error, refetch } = useMyPatientProfileQuery(undefined, { refetchOnMountOrArgChange: true });
  const profile = data?.data || null;
  const notFound = isError && error?.status === 404;
  const { data: historyData } = useMyPatientDocumentsQuery(undefined, { skip: !profile });
  const history = historyData?.data || [];
  const [save, { isLoading: saving }] = useSavePatientProfileMutation();

  const [form, setForm] = useState(EMPTY_FORM);
  const [baseline, setBaseline] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [geoError, setGeoError] = useState('');
  const [geoConfirm, setGeoConfirm] = useState(null);

  const dirty = useMemo(() => FIELD_ORDER.some((k) => form[k] !== baseline[k]), [form, baseline]);
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  useUnsavedChangesGuard(dirty && !saving);

  // Load server values — never over edits in progress. First-time users start from their account.
  useEffect(() => {
    if (dirtyRef.current) return;
    if (profile || notFound) {
      const f = toForm(profile, user);
      setForm(f);
      setBaseline(profile ? f : EMPTY_FORM);
    }
  }, [profile, notFound, user]);

  const token = () => store.getState().auth?.accessToken;
  const { items, percent } = completion(form, profile);
  const overall = profile?.verificationStatus || 'PROFILE_INCOMPLETE';

  // ---------- field binding ----------
  const set = (name, value) => {
    const next = { ...form, [name]: value };
    setForm(next);
    if (errors[name]) setErrors((prev) => { const o = { ...prev }; const m = validate(next)[name]; if (m) o[name] = m; else delete o[name]; return o; });
  };
  const bind = (name) => ({
    id: fieldId(name), name, value: form[name] ?? '', error: errors[name], disabled: saving,
    onChange: (e) => set(name, e.target.value),
    onBlur: () => { const m = validate(form)[name]; setErrors((p) => { const o = { ...p }; if (m) o[name] = m; else delete o[name]; return o; }); },
  });

  // ---------- geolocation ----------
  const locate = async () => {
    setGeoError('');
    if (!navigator.geolocation) { setGeoError('Location is not supported by this browser. Coordinates are optional.'); return; }
    try {
      const { latitude, longitude } = await geo.request();
      const next = { latitude: latitude.toFixed(7), longitude: longitude.toFixed(7) };
      if (form.latitude || form.longitude) setGeoConfirm(next);
      else setForm((f) => ({ ...f, ...next }));
    } catch (err) {
      setGeoError(GEO_ERRORS[err?.code] || 'Could not get your location. Coordinates are optional.');
    }
  };

  // ---------- save ----------
  const onSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    const errs = validate(form, todayIso());
    setErrors(errs);
    if (Object.keys(errs).length) {
      notify.error('Please fix the highlighted fields');
      const first = FIELD_ORDER.find((k) => errs[k]);
      if (first) document.getElementById(fieldId(first))?.focus();
      return;
    }
    try {
      const res = await save(toPayload(form)).unwrap();
      setBaseline(form);
      const changed = res?.data;
      notify.success(changed?.identity?.status === 'PENDING' && profile?.identity?.status === 'VERIFIED'
        ? 'Saved. Your identity will be re-checked because your name or date of birth changed.'
        : changed?.bloodGroupVerification?.status === 'PENDING' && profile?.bloodGroupVerification?.status === 'VERIFIED'
          ? 'Saved. Your blood group will be re-checked because it changed.'
          : 'Profile saved');
    } catch (err) {
      const { message, fieldErrors } = parseApiError(err);
      setServerError(message);
      setErrors((p) => ({ ...p, ...fieldErrors }));
      notify.error(message);
    }
  };

  const uploadDoc = async (documentType, file, onProgress) => {
    await uploadWithProgress('/patients/me/documents', file, token(), onProgress, { documentType });
    dispatch(apiSlice.util.invalidateTags(['User']));
    notify.success('Document submitted for verification');
  };
  const viewDoc = (docId) => openPrivateFile(`/patients/me/documents/${docId}/file`, token())
    .catch((err) => notify.error(err.message));

  const docsDisabled = !profile
    ? 'Save your profile first, then upload documents.'
    : !profile.privacyAcknowledgedAt
      ? 'Read the privacy notice below, tick the acknowledgement and save before uploading documents.'
      : null;

  const groupChangedSinceVerify = profile?.verifiedBloodGroup && form.bloodGroup && form.bloodGroup !== profile.verifiedBloodGroup;

  return (
    <div className="mx-auto max-w-5xl pb-4">
      <PageHeader icon={UserRound} breadcrumb="Home / Profile" title="My Profile"
                  subtitle="Your details and verification help hospitals act on your requests quickly." />

      {isError && !notFound && (
        <Alert type="error" title="Could not load your profile">
          {parseApiError(error).message} <button type="button" className="font-semibold underline" onClick={refetch}>Retry</button>
        </Alert>
      )}

      <ProfileSummaryCard
        name={form.fullName || user?.fullName} email={user?.email} roleLabel="Patient" completion={percent}
        badges={isLoading ? <Skeleton className="h-5 w-40" /> : (
          <>
            <Badge tone={OVERALL_META[overall].tone} dot>{OVERALL_META[overall].label}</Badge>
            <Badge tone={TRACK_META[profile?.identity?.status || 'NOT_SUBMITTED'].tone}>
              Identity: {TRACK_META[profile?.identity?.status || 'NOT_SUBMITTED'].label}
            </Badge>
            <Badge tone={profile?.bloodGroupVerified ? 'success' : 'neutral'}>
              Blood group: {profile?.bloodGroupVerified ? `${profile.verifiedBloodGroup} verified`
                : TRACK_META[profile?.bloodGroupVerification?.status || 'NOT_SUBMITTED'].label}
            </Badge>
          </>
        )}
        completionNote={<p className="mt-1 text-xs text-slate-500">Completeness is not verification — only an administrator can verify.</p>}
      />

      {profile && <Alert type={overall === 'VERIFIED' ? 'success' : overall === 'REJECTED' ? 'error' : overall === 'PROFILE_INCOMPLETE' ? 'info' : 'warning'}
                         title={OVERALL_META[overall].label}>{OVERALL_META[overall].text}</Alert>}

      {isLoading ? (
        <div className="space-y-6">{[...Array(3)].map((_, i) => <Skeleton key={i} className="h-56 rounded-2xl" />)}</div>
      ) : (
        <form noValidate onSubmit={onSubmit} aria-busy={saving}>
          <Alert type="error" title="Could not save">{serverError}</Alert>

          {/* 1. Personal */}
          <Section id="personal" icon={UserRound} title="Personal information">
            <div className={grid}>
              <TextField className={full} label={<Label required>Full name</Label>} autoComplete="name" aria-required="true"
                         maxLength={100} hint="As shown on your identity document" {...bind('fullName')} />
              <TextField type="date" label={<Label required>Date of birth</Label>} aria-required="true"
                         max={todayIso()} {...bind('dateOfBirth')} />
              <div>
                <label htmlFor={fieldId('bloodGroup')} className="mb-1.5 flex items-center gap-2 text-sm font-medium text-slate-700 dark:text-slate-200">
                  <Label required>Blood group</Label>
                  {profile?.bloodGroupVerified && !groupChangedSinceVerify && (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-success"><BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" /> Verified</span>
                  )}
                </label>
                <Select id={fieldId('bloodGroup')} value={form.bloodGroup} onChange={(e) => set('bloodGroup', e.target.value)}
                        error={errors.bloodGroup} disabled={saving} aria-required="true">
                  <option value="">Select…</option>
                  {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
                </Select>
                {groupChangedSinceVerify && (
                  <p className="mt-1.5 flex items-start gap-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                    <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    Saving a different group sends your blood-group document back for review; it won't count as verified until approved.
                  </p>
                )}
              </div>
              <Select id={fieldId('gender')} label={<Label optional>Gender</Label>} value={form.gender}
                      onChange={(e) => set('gender', e.target.value)} disabled={saving}>
                <option value="">Not specified</option>
                {GENDERS.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
              </Select>
              <div>
                <TextField type="tel" inputMode="tel" label={<Label required>Mobile number</Label>} autoComplete="tel"
                           aria-required="true" placeholder="10-digit mobile" {...bind('phone')} />
                <p className="mt-1 inline-flex items-center gap-1 text-xs text-slate-500">
                  <Badge tone="neutral">Not verified</Badge> Mobile verification by SMS isn't available yet.
                </p>
              </div>
              <div className={full}>
                <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Email address</span>
                <p className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-200">
                  <Mail className="h-4 w-4 text-slate-400" aria-hidden="true" /> {profile?.email || user?.email}
                  <Badge tone="success" className="ml-auto">Verified at sign-up</Badge>
                </p>
              </div>
            </div>
          </Section>

          {/* 2. Address */}
          <Section id="address" icon={MapPin} title="Address and location"
                   description="Coordinates are optional; they help find blood banks near you.">
            <div className={grid}>
              <TextField className={full} label={<Label required>Full address</Label>} autoComplete="street-address"
                         aria-required="true" maxLength={255} {...bind('address')} />
              <TextField label={<Label required>City</Label>} autoComplete="address-level2" aria-required="true" maxLength={50} {...bind('city')} />
              <Select id={fieldId('state')} label={<Label required>State</Label>} value={form.state} error={errors.state}
                      onChange={(e) => set('state', e.target.value)} disabled={saving} aria-required="true">
                <option value="">Select state / UT…</option>
                {(form.state && !INDIAN_STATES.includes(form.state) ? [form.state, ...INDIAN_STATES] : INDIAN_STATES)
                  .map((s) => <option key={s} value={s}>{s}</option>)}
              </Select>
              <TextField label={<Label required>PIN code</Label>} inputMode="numeric" autoComplete="postal-code"
                         aria-required="true" maxLength={6} {...bind('pincode')} />
              <div className="hidden md:block" aria-hidden="true" />
              <TextField label={<Label optional>Latitude</Label>} inputMode="decimal" {...bind('latitude')} />
              <TextField label={<Label optional>Longitude</Label>} inputMode="decimal" {...bind('longitude')} />
            </div>
            <div className="mt-4">
              <Button type="button" variant="secondary" loading={geo.loading} disabled={saving} onClick={locate}
                      leftIcon={<Navigation className="h-4 w-4" />}>Use my current location</Button>
            </div>
            {geoError && <div className="mt-3"><Alert type="warning">{geoError}</Alert></div>}
          </Section>

          {/* 3. Documents */}
          <Section id="documents" icon={ShieldCheck} title="Verification documents"
                   description="Identity and blood group are verified separately by an administrator.">
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <PatientDocumentCard
                id="identity-doc" title="Identity document"
                description="Any one government-issued photo ID. Aadhaar is optional."
                guidance="Upload a clear copy showing your name and photo. For Aadhaar, a masked copy (only the last 4 digits visible) is enough. ID numbers are not recorded."
                track={profile?.identity} typeOptions={IDENTITY_TYPES}
                history={history.filter((h) => h.category === 'IDENTITY')}
                onUpload={uploadDoc} onView={viewDoc} disabledReason={docsDisabled} />
              <PatientDocumentCard
                id="blood-doc" title="Blood-group document"
                description="A lab report or authorised medical record showing your blood group."
                guidance="The document must show your name and blood group (ABO and Rh)."
                track={profile?.bloodGroupVerification} typeOptions={BLOOD_GROUP_DOC_TYPES}
                history={history.filter((h) => h.category === 'BLOOD_GROUP')}
                onUpload={uploadDoc} onView={viewDoc} disabledReason={docsDisabled}>
                {profile?.bloodGroupVerified
                  ? <p className="mb-3 text-sm text-success">Verified group on file: <b>{profile.verifiedBloodGroup}</b></p>
                  : profile?.bloodGroupVerification?.status === 'VERIFIED' && (
                    <p className="mb-3 text-sm text-amber-700 dark:text-amber-300">The verified document doesn't match your current profile group.</p>
                  )}
              </PatientDocumentCard>
            </div>
          </Section>

          {/* 4. Emergency + preferences */}
          <Section id="preferences" icon={Phone} title="Emergency contact and preferences">
            <div className={grid}>
              <TextField label={<Label optional>Emergency contact name</Label>} maxLength={100} {...bind('emergencyContactName')} />
              <TextField type="tel" inputMode="tel" label={<Label optional>Emergency contact phone</Label>} {...bind('emergencyContactPhone')} />
              <RadioGroup id={fieldId('notificationConsent')} name="notificationConsent" className={full} columns={2}
                          legend="Request update emails" value={form.notificationConsent}
                          onChange={(v) => set('notificationConsent', v)}
                          options={[
                            { value: 'YES', label: 'Yes, email me request updates', hint: 'Status changes, donor matches and hospital messages.' },
                            { value: 'NO', label: 'No emails', hint: 'You will still see every update in the app.' },
                          ]}
                          hint={profile?.consentUpdatedAt ? 'Your choice is saved with the date you made it.' : undefined} />
            </div>
            <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/50 dark:text-slate-300">
              <p className="mb-2 flex items-center gap-2 font-semibold text-slate-800 dark:text-slate-100"><Lock className="h-4 w-4" aria-hidden="true" /> Privacy notice</p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Your details are used to process your blood requests and to let hospitals and blood banks contact you about them.</li>
                <li>Documents are stored privately. Only you and platform administrators can open them; they are never shared through public links.</li>
                <li>Donors and blood banks see the blood group, units and location needed — not your name, age or documents.</li>
                <li>We do not record ID numbers. Every document you upload is kept as a record of what was verified.</li>
              </ul>
              <Checkbox id={fieldId('privacyAcknowledged')} className="mt-3" checked={!!form.privacyAcknowledged}
                        disabled={saving || !!profile?.privacyAcknowledgedAt}
                        onChange={(v) => set('privacyAcknowledged', v)}
                        label={profile?.privacyAcknowledgedAt ? 'You acknowledged this notice.' : 'I have read and understood how my information and documents are used (required before uploading documents).'} />
            </div>
          </Section>

          {/* sticky actions */}
          <div className="sticky bottom-0 z-20 -mx-5 border-t border-slate-200 bg-white/90 px-5 py-3 backdrop-blur dark:border-slate-800 dark:bg-slate-950/90 sm:mx-0 sm:rounded-2xl sm:border sm:shadow-soft">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-500" aria-live="polite">
                {dirty ? 'You have unsaved changes' : profile ? 'All changes saved' : 'Not saved yet'}
                <span className="ml-2 hidden text-xs sm:inline">· Completeness {percent}% ({items.filter((i) => !i.done).length} left)</span>
              </p>
              <div className="flex gap-2">
                <Button type="button" variant="secondary" disabled={!dirty || saving}
                        onClick={() => { setForm(baseline); setErrors({}); setServerError(''); }}>Cancel</Button>
                <Button type="submit" loading={saving} disabled={!dirty && !!profile} leftIcon={<Save className="h-4 w-4" />}>Save changes</Button>
              </div>
            </div>
          </div>
        </form>
      )}

      <Modal open={!!geoConfirm} onClose={() => setGeoConfirm(null)} title="Replace saved coordinates?"
             footer={<>
               <Button variant="secondary" onClick={() => setGeoConfirm(null)}>Keep current</Button>
               <Button onClick={() => { setForm((f) => ({ ...f, ...geoConfirm })); setGeoConfirm(null); }}>Use new location</Button>
             </>}>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Current: <span className="font-mono">{form.latitude}, {form.longitude}</span><br />
          New: <span className="font-mono">{geoConfirm?.latitude}, {geoConfirm?.longitude}</span>
        </p>
      </Modal>
    </div>
  );
}
