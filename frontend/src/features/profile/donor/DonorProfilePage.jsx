import { useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { UserRound, Save, RotateCcw } from 'lucide-react';
import { useAuth } from '../../../hooks/useAuth';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { useGoogleMaps } from '../../../hooks/useGoogleMaps';
import {
  useGetMyProfileQuery, useUpsertMyProfileMutation, useUploadDonorPhotoMutation, useRemoveDonorPhotoMutation,
} from '../profileApi';
import { useMyDonationsQuery } from '../../donations/donationsApi';
import { userUpdated } from '../../auth/authSlice';
import { useUnsavedChangesGuard } from '../bloodbank/useUnsavedChangesGuard';
import { fetchObjectUrl } from '../../../lib/privateFiles';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { ROLE_LABEL } from '../../../lib/constants';
import PageHeader from '../../../components/layout/PageHeader';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import ProfileSummaryCard from '../ProfileSummaryCard';
import { PersonalSection, LocationSection, HistorySection, PreferencesSection, HealthSection } from './DonorProfileSections';
import {
  toForm, toPayload, validate, profileCompletion, screeningStarted, fieldId, FIELD_ORDER, EMPTY_SCREENING,
} from './donorProfileModel';

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const capitalise = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);

/** Move focus to the first field (in page order) that has an error. */
function focusFirst(errors) {
  const name = FIELD_ORDER.find((n) => errors[n]) || Object.keys(errors)[0];
  const el = name && document.getElementById(fieldId(name));
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  (el.tagName === 'FIELDSET' ? el.querySelector('input') : el)?.focus({ preventScroll: true });
}

/**
 * The donor's own profile at /donor/profile: personal details, address and
 * location, donation history, preferences and an optional health
 * questionnaire, saved together with one PUT /donors/me.
 *
 * Completion, account verification and medical eligibility are kept apart:
 * completion only measures how much is filled in, verification reflects what
 * the account has actually confirmed, and eligibility is never decided here.
 */
export default function DonorProfilePage() {
  const { user } = useAuth();
  const dispatch = useDispatch();
  const token = useSelector((s) => s.auth.accessToken);

  const { data, isLoading, isError, error, refetch } = useGetMyProfileQuery('donors');
  const donations = useMyDonationsQuery();
  const [upsert, { isLoading: saving }] = useUpsertMyProfileMutation();
  const [uploadPhoto, { isLoading: uploading }] = useUploadDonorPhotoMutation();
  const [removePhoto, { isLoading: removing }] = useRemoveDonorPhotoMutation();
  const geo = useGeolocation();
  const { status: maps } = useGoogleMaps();

  const profile = data?.data ?? null;
  const notFound = isError && error?.status === 404; // first-time donor: start from a blank form
  const loadFailed = isError && !notFound;

  // Saved state as form values; the form resets to it unless there are unsaved edits.
  const saved = useMemo(() => (profile || notFound ? toForm(profile, user) : null), [profile, notFound, user]);
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
  const errorFor = (name) =>
    capitalise(server.fieldErrors[name]) || ((submitted || touched[name]) ? clientErrors[name] : undefined);
  const errorCount = Object.keys(clientErrors).length;

  // ---------- field helpers ----------
  const clearServerError = (name) =>
    setServer((s) => {
      if (!s.fieldErrors[name]) return s;
      const fieldErrors = { ...s.fieldErrors };
      delete fieldErrors[name];
      return { ...s, fieldErrors };
    });
  const touch = (name) => setTouched((t) => (t[name] ? t : { ...t, [name]: true }));
  const set = (name, value) => {
    setForm((f) => ({ ...f, [name]: value }));
    clearServerError(name);
  };
  const bind = (name) => ({
    id: fieldId(name),
    value: form[name],
    onChange: (e) => set(name, e.target.value),
    onBlur: () => touch(name),
    error: errorFor(name),
  });
  const bindRadio = (name) => ({
    id: fieldId(name),
    name: fieldId(name),
    value: form[name],
    onChange: (v) => { set(name, v); touch(name); },
    error: errorFor(name),
  });

  const setAnswer = (question, value) => {
    // A changed answer must be confirmed again.
    setForm((f) => ({ ...f, screening: { ...f.screening, [question]: value }, declarationAccepted: false }));
    clearServerError(`healthScreening.${question}`);
    touch(`healthScreening.${question}`);
  };
  const setDeclaration = (checked) => {
    setForm((f) => ({ ...f, declarationAccepted: checked }));
    clearServerError('healthScreening.declarationAccepted');
  };
  const clearScreening = () => {
    setForm((f) => ({ ...f, screening: { ...EMPTY_SCREENING }, declarationAccepted: false }));
    notify.success('Answers cleared. Save your profile to remove them.');
  };

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
        type: 'success',
        text: `Location captured${accuracy ? ` (accurate to about ${Math.round(accuracy)} m)` : ''}. Save your profile to keep it.`,
      });
    } catch { /* surfaced via geo.error */ }
  };

  const findFromAddress = async () => {
    geo.clearError();
    if (!form.city.trim() && !form.pincode.trim()) {
      setLocationNote({ type: 'warning', text: 'Enter at least your city or PIN code first.' });
      return;
    }
    const address = [form.address, form.city, form.state, form.pincode, 'India'].map((s) => s.trim()).filter(Boolean).join(', ');
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

  // ---------- photo (private file, fetched with the session token) ----------
  const hasPhoto = Boolean(profile?.hasPhoto);
  const [photoUrl, setPhotoUrl] = useState(null);
  const [photoVersion, setPhotoVersion] = useState(0);
  useEffect(() => {
    if (!hasPhoto || !token) { setPhotoUrl(null); return undefined; }
    let url = null;
    let cancelled = false;
    fetchObjectUrl('/donors/me/photo', token)
      .then((u) => { if (cancelled) URL.revokeObjectURL(u); else { url = u; setPhotoUrl(u); } })
      .catch(() => { if (!cancelled) setPhotoUrl(null); });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [hasPhoto, photoVersion, token]);

  const photo = {
    url: photoUrl,
    uploading,
    removing,
    onError: (message) => notify.error(message),
    onUpload: async (file) => {
      try {
        await uploadPhoto(file).unwrap();
        setPhotoVersion((v) => v + 1);
        notify.success('Photo updated');
      } catch (e) {
        notify.error(parseApiError(e).message);
      }
    },
    onRemove: async () => {
      try {
        await removePhoto().unwrap();
        notify.success('Photo removed');
      } catch (e) {
        notify.error(parseApiError(e).message);
      }
    },
  };

  // ---------- save ----------
  const onSubmit = async (e) => {
    e.preventDefault();
    setSubmitted(true);
    setServer({ message: '', fieldErrors: {} });
    if (errorCount) {
      focusFirst(clientErrors);
      return;
    }
    try {
      const res = await upsert({ resource: 'donors', body: toPayload(form) }).unwrap();
      const next = toForm(res.data, user);
      lastSaved.current = next;
      setForm(next);
      setTouched({});
      setSubmitted(false);
      dispatch(userUpdated({ fullName: res.data.fullName }));
      notify.success('Profile saved');
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
  const emailVerified = profile ? profile.emailVerified : user?.emailVerified;
  const badges = (
    <>
      <Badge tone={emailVerified ? 'success' : 'warning'} dot>{emailVerified ? 'Email verified' : 'Email not verified'}</Badge>
      {profile?.phone && <Badge tone="neutral" dot>Mobile not verified</Badge>}
      {profile?.availability && (
        <Badge tone={profile.availability === 'AVAILABLE' ? 'success' : 'neutral'} dot>
          {profile.availability === 'AVAILABLE' ? 'Available' : 'Temporarily unavailable'}
        </Badge>
      )}
      {profile?.contactConsent === false && <Badge tone="warning" dot>Not receiving requests</Badge>}
    </>
  );
  const completionNote = (
    <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
      {missing.length
        ? `Still to add: ${missing.slice(0, 3).join(', ')}${missing.length > 3 ? ` and ${missing.length - 3} more` : ''}. `
        : 'Your profile is complete. '}
      Completion isn't an eligibility check — staff confirm that at each donation.
    </p>
  );
  const statusText = submitted && errorCount
    ? `Please fix ${errorCount} highlighted field${errorCount === 1 ? '' : 's'}.`
    : dirty ? 'You have unsaved changes.' : notFound ? 'Complete the required fields and save to create your donor profile.' : 'All changes saved.';

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader icon={UserRound} breadcrumb="Home / Profile" title="My Profile"
                  subtitle="Keep your details current so matching stays accurate." />

      {loadFailed && (
        <Alert type="error" title="Could not load your profile">
          <span className="mr-2">Check your connection and try again.</span>
          <button type="button" onClick={refetch} className="font-semibold underline">Retry</button>
        </Alert>
      )}
      <Alert type="error">{server.message && !Object.keys(server.fieldErrors).length ? server.message : ''}</Alert>

      <ProfileSummaryCard
        name={form?.fullName.trim() || profile?.fullName || user?.fullName}
        email={profile?.email ?? user?.email}
        roleLabel={ROLE_LABEL.DONOR}
        completion={percent}
        photoUrl={photoUrl}
        badges={badges}
        completionNote={completionNote}
      />

      {isLoading || (!form && !loadFailed) ? (
        <div className="space-y-6">
          {[...Array(3)].map((_, i) => <Skeleton key={i} className="h-48 rounded-2xl" />)}
        </div>
      ) : form && (
        <form noValidate onSubmit={onSubmit} aria-describedby="donor-required-note">
          <p id="donor-required-note" className="mb-4 text-sm text-slate-500">
            Fields marked <span className="text-brand-600" aria-hidden="true">*</span><span className="sr-only">with an asterisk</span> are required.
          </p>

          <PersonalSection form={form} bind={bind} profile={profile} user={user} photo={photo} />
          <LocationSection form={form} bind={bind} set={set} geo={geo} onUseLocation={locateMe}
                           maps={maps} onGeocode={findFromAddress} geocoding={geocoding} locationNote={locationNote} />
          <HistorySection form={form} bind={bind} bindRadio={bindRadio} donations={donations} />
          <PreferencesSection bindRadio={bindRadio} profile={profile} />
          <HealthSection form={form} bindRadio={bindRadio} setAnswer={setAnswer} setDeclaration={setDeclaration}
                         clearScreening={clearScreening} started={screeningStarted(form)} error={errorFor} profile={profile} />

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
    </div>
  );
}
