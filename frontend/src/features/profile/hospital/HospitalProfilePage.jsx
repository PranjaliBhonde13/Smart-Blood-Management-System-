import { useEffect, useMemo, useRef, useState } from 'react';
import { UserRound, Building2, PhoneCall, MapPin, Navigation, Search, X, Save, RotateCcw } from 'lucide-react';
import { useAuth } from '../../../hooks/useAuth';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { useGoogleMaps } from '../../../hooks/useGoogleMaps';
import { useGetMyProfileQuery, useUpsertMyProfileMutation } from '../profileApi';
import { useUnsavedChangesGuard } from '../bloodbank/useUnsavedChangesGuard';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { ROLE_LABEL } from '../../../lib/constants';
import PageHeader from '../../../components/layout/PageHeader';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import RadioGroup from '../../../components/ui/RadioGroup';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import ProfileSummaryCard from '../ProfileSummaryCard';
import { FULL, Label, Section } from '../FormSection';
import HospitalVerificationOverview from './HospitalVerificationOverview';
import HospitalDocumentsSection from './HospitalDocumentsSection';
import { OVERALL_STATUS } from './documentStatus';
import {
  CATEGORIES, BLOOD_BANK_OPTIONS, INDIAN_STATES, toForm, toPayload, validate, sameAsMainPhone,
  profileCompletion, FIELD_ORDER, fieldId,
} from './hospitalProfileModel';

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const capitalise = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);

function focusFirst(errors) {
  const name = FIELD_ORDER.find((n) => errors[n]) || Object.keys(errors)[0];
  const el = name && document.getElementById(fieldId(name));
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  (el.tagName === 'FIELDSET' ? el.querySelector('input') : el)?.focus({ preventScroll: true });
}

/**
 * The hospital's own profile at /hospital/profile: identity and registration,
 * contacts for coordinating blood, address and location, then Documents &
 * Verification. Profile completion, document verification and administrator
 * approval are shown separately; only the server decides verification.
 */
export default function HospitalProfilePage() {
  const { user } = useAuth();
  const { data, isLoading, isError, error, refetch } = useGetMyProfileQuery('hospitals');
  const [upsert, { isLoading: saving }] = useUpsertMyProfileMutation();
  const geo = useGeolocation();
  const { status: maps } = useGoogleMaps();

  const profile = data?.data ?? null;
  const notFound = isError && error?.status === 404;
  const loadFailed = isError && !notFound;

  const saved = useMemo(() => (profile || notFound ? toForm(profile) : null), [profile, notFound]);
  const [form, setForm] = useState(null);
  const lastSaved = useRef(null);
  useEffect(() => {
    if (!saved) return;
    setForm((cur) => (cur && lastSaved.current && !same(cur, lastSaved.current) ? cur : saved));
    lastSaved.current = saved;
  }, [saved]);

  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [server, setServer] = useState({ message: '', fieldErrors: {} });
  const [locationNote, setLocationNote] = useState(null);
  const [geocoding, setGeocoding] = useState(false);

  const dirty = Boolean(form && saved && !same(form, saved));
  useUnsavedChangesGuard(dirty);

  const clientErrors = useMemo(() => (form ? validate(form) : {}), [form]);
  const errorCount = Object.keys(clientErrors).length;
  const errorFor = (name) =>
    capitalise(server.fieldErrors[name]) || ((submitted || touched[name]) ? clientErrors[name] : undefined);

  const clearServerError = (name) =>
    setServer((s) => {
      if (!s.fieldErrors[name]) return s;
      const fieldErrors = { ...s.fieldErrors };
      delete fieldErrors[name];
      return { ...s, fieldErrors };
    });
  const touch = (name) => setTouched((t) => (t[name] ? t : { ...t, [name]: true }));
  const set = (name, value) => { setForm((f) => ({ ...f, [name]: value })); clearServerError(name); };
  const bind = (name) => ({
    id: fieldId(name),
    value: form[name],
    onChange: (e) => set(name, e.target.value),
    onBlur: () => touch(name),
    error: errorFor(name),
  });

  // ---------- location ----------
  const setCoordinates = (lat, lng) => {
    set('latitude', lat.toFixed(7));
    set('longitude', lng.toFixed(7));
    touch('latitude');
    touch('longitude');
  };
  const locateMe = async () => {
    setLocationNote(null);
    try {
      const { latitude, longitude, accuracy } = await geo.request();
      setCoordinates(latitude, longitude);
      setLocationNote({
        type: 'warning',
        text: `Captured your device's location${accuracy ? ` (about ${Math.round(accuracy)} m accuracy)` : ''}. `
          + 'Use this only if you are at the hospital, then save.',
      });
    } catch { /* surfaced via geo.error */ }
  };
  const findFromAddress = async () => {
    geo.clearError();
    if (!form.city.trim() && !form.pincode.trim()) {
      setLocationNote({ type: 'warning', text: 'Enter at least the city or PIN code first.' });
      return;
    }
    const address = [form.hospitalName, form.address, form.city, form.state, form.pincode, 'India']
      .map((s) => s.trim()).filter(Boolean).join(', ');
    setGeocoding(true);
    setLocationNote(null);
    try {
      const { results } = await new window.google.maps.Geocoder().geocode({ address, region: 'in' });
      const loc = results[0].geometry.location;
      setCoordinates(loc.lat(), loc.lng());
      setLocationNote({ type: 'success', text: `Found “${results[0].formatted_address}”. Check it looks right, then save.` });
    } catch {
      setLocationNote({ type: 'error', text: "We couldn't find coordinates for that address. Use your current location or enter them manually." });
    } finally {
      setGeocoding(false);
    }
  };

  // ---------- save ----------
  const onSubmit = async (e) => {
    e.preventDefault();
    setSubmitted(true);
    setServer({ message: '', fieldErrors: {} });
    if (errorCount) { focusFirst(clientErrors); return; }
    try {
      const res = await upsert({ resource: 'hospitals', body: toPayload(form) }).unwrap();
      const next = toForm(res.data);
      lastSaved.current = next;
      setForm(next);
      setTouched({});
      setSubmitted(false);
      notify.success(res.message || 'Profile saved');
    } catch (err) {
      const parsed = parseApiError(err);
      setServer(parsed);
      if (Object.keys(parsed.fieldErrors).length) focusFirst(parsed.fieldErrors);
      else window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };
  const discard = () => {
    setForm(saved);
    setTouched({});
    setSubmitted(false);
    setServer({ message: '', fieldErrors: {} });
    setLocationNote(null);
  };

  // ---------- render ----------
  const { percent, missing } = profileCompletion(profile);
  const meta = profile?.verificationStatus ? OVERALL_STATUS[profile.verificationStatus] : null;
  const identityChanged = profile?.verified && form
    && (form.hospitalName.trim() !== profile.hospitalName || form.licenseNumber.trim() !== profile.licenseNumber);
  const states = form?.state && !INDIAN_STATES.includes(form.state) ? [form.state, ...INDIAN_STATES] : INDIAN_STATES;
  const hasCoords = form && (form.latitude.trim() !== '' || form.longitude.trim() !== '');
  const statusText = submitted && errorCount
    ? `Please fix ${errorCount} highlighted field${errorCount === 1 ? '' : 's'}.`
    : dirty ? 'You have unsaved changes.' : notFound ? 'Complete the required fields and save to register your hospital.' : 'All changes saved.';

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader icon={UserRound} breadcrumb="Home / Profile" title="My Profile"
                  subtitle="Keep your hospital's details current so requests and verification run smoothly." />

      {loadFailed && (
        <Alert type="error" title="Could not load your profile">
          <span className="mr-2">Check your connection and try again.</span>
          <button type="button" onClick={refetch} className="font-semibold underline">Retry</button>
        </Alert>
      )}
      {profile && <HospitalVerificationOverview profile={profile} />}
      <Alert type="error">{server.message && !Object.keys(server.fieldErrors).length ? server.message : ''}</Alert>

      <ProfileSummaryCard
        name={form?.hospitalName.trim() || profile?.hospitalName || user?.fullName}
        email={user?.email}
        roleLabel={ROLE_LABEL.HOSPITAL}
        completion={percent}
        badges={meta ? <Badge tone={meta.tone} dot>{meta.label}</Badge> : null}
        completionNote={(
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
            {missing.length
              ? `Still to add: ${missing.slice(0, 3).join(', ')}${missing.length > 3 ? ` and ${missing.length - 3} more` : ''}.`
              : 'All profile details added.'} Verification is separate and done by an administrator.
          </p>
        )}
      />

      {isLoading || (!form && !loadFailed) ? (
        <div className="space-y-6">{[...Array(3)].map((_, i) => <Skeleton key={i} className="h-48 rounded-2xl" />)}</div>
      ) : form && (
        <form noValidate onSubmit={onSubmit} aria-describedby="hospital-required-note">
          <p id="hospital-required-note" className="mb-4 text-sm text-slate-500">
            Fields marked <span className="text-brand-600" aria-hidden="true">*</span><span className="sr-only">with an asterisk</span> are required.
          </p>

          <Section id="hospital-identity" icon={Building2} title="Hospital & registration"
                   description="Your registration category and blood bank status decide which documents you need to upload.">
            <TextField {...bind('hospitalName')} className={FULL} label={<Label required>Hospital name</Label>} required maxLength={100}
                       autoComplete="organization" />
            <TextField {...bind('licenseNumber')} label={<Label required>Registration / license number</Label>} required maxLength={50} />
            <Select {...bind('registrationCategory')} label={<Label required>Registration category</Label>} required>
              <option value="">Select…</option>
              {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </Select>
            {identityChanged && (
              <div className={FULL}><Alert type="warning">
                Changing the hospital name or license number sends your hospital back to an administrator for approval.
              </Alert></div>
            )}
            <RadioGroup id={fieldId('operatesBloodBank')} name={fieldId('operatesBloodBank')} className={FULL} required
                        legend={<Label required>Does your hospital run its own blood bank?</Label>}
                        options={BLOOD_BANK_OPTIONS} value={form.operatesBloodBank}
                        onChange={(v) => { set('operatesBloodBank', v); touch('operatesBloodBank'); }}
                        error={errorFor('operatesBloodBank')} />
          </Section>

          <Section id="hospital-contacts" icon={PhoneCall} title="Contacts"
                   description="Who donors, patients and coordinators should reach about blood requests.">
            <TextField {...bind('phone')} type="tel" inputMode="tel" autoComplete="tel" required
                       label={<Label required>Main phone</Label>} placeholder="022 2654 3210"
                       hint="Landline with STD code, or a mobile number." />
            <TextField {...bind('emergencyPhone')} type="tel" inputMode="tel" label="24×7 emergency / blood desk phone"
                       hint={sameAsMainPhone(form) ? 'Same as the main phone — add a separate round-the-clock number if you have one.'
                         : 'A number answered round the clock for urgent blood requests.'} />
            <TextField {...bind('contactPersonName')} label="Contact person" autoComplete="name" maxLength={100}
                       placeholder="e.g. Dr. Meera Iyer" />
            <TextField {...bind('contactPersonDesignation')} label="Designation" maxLength={100}
                       placeholder="e.g. Blood transfusion officer" />
            <TextField {...bind('officialEmail')} type="email" autoComplete="email" label="Official email" maxLength={150} />
            <TextField id={fieldId('accountEmail')} type="email" label="Sign-in email" value={user?.email || ''} readOnly
                       hint="Used to sign in; can't be changed here." />
          </Section>

          <Section id="hospital-location" icon={MapPin} title="Address & location"
                   description="Shown to patients looking for nearby hospitals.">
            <TextField {...bind('address')} className={FULL} label={<Label required>Address</Label>} required maxLength={255}
                       autoComplete="street-address" placeholder="Building, street, area" />
            <TextField {...bind('city')} label={<Label required>City</Label>} required maxLength={50} autoComplete="address-level2" />
            <Select {...bind('state')} label={<Label required>State / union territory</Label>} required>
              <option value="">Select…</option>
              {states.map((s) => <option key={s} value={s}>{s}</option>)}
            </Select>
            <TextField {...bind('pincode')} label={<Label required>PIN code</Label>} required inputMode="numeric" maxLength={6}
                       autoComplete="postal-code" placeholder="400058" />
            <div className="hidden md:block" />

            <div className={`${FULL} rounded-2xl border border-slate-200 p-4 dark:border-slate-700`}>
              <p className="text-sm font-medium text-slate-700 dark:text-slate-200">Map location</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Coordinates put your hospital in nearby-hospital search and help match requests to donors close by.
                Without them your hospital won't appear in location-based results.
              </p>
              {geo.permission === 'denied' && (
                <div className="mt-3"><Alert type="warning">
                  Location access is blocked for this site. Allow it in your browser settings, or type the coordinates below.
                </Alert></div>
              )}
              {geo.error && <div className="mt-3"><Alert type="error">{geo.error}</Alert></div>}
              {locationNote && <div className="mt-3"><Alert type={locationNote.type}>{locationNote.text}</Alert></div>}
              <div className="mt-3 flex flex-wrap gap-2">
                {maps === 'ready' && (
                  <Button type="button" variant="secondary" size="sm" loading={geocoding} onClick={findFromAddress}
                          leftIcon={<Search className="h-4 w-4" />}>Find from address</Button>
                )}
                <Button type="button" variant="secondary" size="sm" loading={geo.loading} onClick={locateMe}
                        leftIcon={<Navigation className="h-4 w-4" />}>Use my current location</Button>
                {hasCoords && (
                  <Button type="button" variant="ghost" size="sm" onClick={() => { set('latitude', ''); set('longitude', ''); }}
                          leftIcon={<X className="h-4 w-4" />}>Clear coordinates</Button>
                )}
              </div>
              <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <TextField {...bind('latitude')} label="Latitude" inputMode="decimal" placeholder="19.1197" hint="Between -90 and 90" />
                <TextField {...bind('longitude')} label="Longitude" inputMode="decimal" placeholder="72.8468" hint="Between -180 and 180" />
              </div>
            </div>
          </Section>

          <div className="sticky bottom-0 z-20 mb-2 rounded-2xl border border-slate-200 bg-white/95 p-3 shadow-soft backdrop-blur dark:border-slate-700 dark:bg-slate-900/95">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p role="status" aria-live="polite"
                 className={`text-sm ${submitted && errorCount ? 'font-medium text-brand-600' : 'text-slate-500 dark:text-slate-400'}`}>
                {statusText}
              </p>
              <div className="flex gap-2">
                {dirty && (
                  <Button type="button" variant="secondary" onClick={discard} disabled={saving}
                          leftIcon={<RotateCcw className="h-4 w-4" />}>Discard</Button>
                )}
                <Button type="submit" loading={saving} className="flex-1 sm:flex-none" leftIcon={<Save className="h-4 w-4" />}>
                  Save profile
                </Button>
              </div>
            </div>
          </div>
        </form>
      )}

      {profile ? (
        <>
          {dirty && (
            <div className="mt-6"><Alert type="info">
              Save your profile first — your registration category and blood bank answer decide which documents are listed below.
            </Alert></div>
          )}
          <HospitalDocumentsSection />
        </>
      ) : !isLoading && !loadFailed && (
        <div className="mt-6"><Alert type="info" title="Documents & Verification">
          Save your hospital profile first. You can then upload your registration certificate, operating license and other documents here.
        </Alert></div>
      )}
    </div>
  );
}
