import { useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useStore } from 'react-redux';
import {
  Building2, Phone, MapPin, FileBadge2, Navigation, Save, Camera, Trash2, CheckCircle2, Circle,
  AlertTriangle, ExternalLink,
} from 'lucide-react';
import { useAuth } from '../../../hooks/useAuth';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { apiSlice } from '../../../app/apiSlice';
import { useGetMyProfileQuery, useUpsertMyProfileMutation } from '../profileApi';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { API_BASE_URL } from '../../../lib/constants';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';
import Skeleton from '../../../components/ui/Skeleton';
import {
  EMPTY_FORM, INDIAN_STATES, LOGO_ACCEPT, LOGO_MAX_BYTES, completion, fromProfile, toBody,
  validateField, validateFile, validateForm,
} from './bloodBankForm';
import { fetchObjectUrl, openPrivateFile, uploadWithProgress } from '../../../lib/privateFiles';
import { useUnsavedChangesGuard } from './useUnsavedChangesGuard';
import LicenseUploader from './LicenseUploader';
import { VerificationBadge, VerificationPanel } from './VerificationStatus';

const RESOURCE = 'blood-banks';
/** Fields whose change invalidates an existing verification decision (mirrors the server). */
const IDENTITY_FIELDS = ['bankName', 'licenseNumber', 'licenseIssueDate', 'licenseExpiryDate'];

const GEO_ERRORS = {
  1: 'Location permission was denied. Allow location access for this site in your browser settings, or enter the coordinates manually.',
  2: 'Your location is currently unavailable. Check that location services are on, or enter the coordinates manually.',
  3: 'Finding your location timed out. Try again, or enter the coordinates manually.',
};

/** Required-field label: visible asterisk, hidden from screen readers (inputs carry aria-required). */
function Req({ children }) {
  return <>{children} <span className="text-brand-600" aria-hidden="true">*</span></>;
}

function Section({ id, icon: Icon, title, description, children }) {
  return (
    <section aria-labelledby={`${id}-title`}>
      <Card className="border border-slate-100 dark:border-slate-800">
        <div className="mb-5 flex items-start gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
            <Icon className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <h2 id={`${id}-title`} className="text-base font-bold text-slate-900 dark:text-white">{title}</h2>
            {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
          </div>
        </div>
        {children}
      </Card>
    </section>
  );
}

/**
 * Blood bank registration & license verification. Owners edit their profile,
 * upload the license certificate (stored privately) and see the server-derived
 * verification status. Saving never verifies: only an administrator can.
 */
export default function BloodBankProfilePage() {
  const { user } = useAuth();
  const store = useStore();
  const dispatch = useDispatch();
  const geo = useGeolocation();

  const { data, isLoading, isError, error, refetch } = useGetMyProfileQuery(RESOURCE);
  const [upsert] = useUpsertMyProfileMutation();
  const profile = data?.data || null;
  const notFound = isError && error?.status === 404; // first-time registration
  const loadError = isError && !notFound;

  const [form, setForm] = useState(EMPTY_FORM);
  const [baseline, setBaseline] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [stagedDoc, setStagedDoc] = useState(null);
  const [docProgress, setDocProgress] = useState(null);
  const [docUploadError, setDocUploadError] = useState('');
  const [docMissingError, setDocMissingError] = useState('');
  const [stagedLogo, setStagedLogo] = useState(null);
  const [logoRemoved, setLogoRemoved] = useState(false);
  const [logoError, setLogoError] = useState('');
  const [savedLogoUrl, setSavedLogoUrl] = useState(null);
  const [stagedLogoUrl, setStagedLogoUrl] = useState(null);
  const [geoError, setGeoError] = useState('');
  const [geoConfirm, setGeoConfirm] = useState(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [saving, setSaving] = useState(false);

  const dirty = useMemo(
    () => stagedDoc != null || stagedLogo != null || logoRemoved
      || Object.keys(EMPTY_FORM).some((k) => (form[k] ?? '') !== (baseline[k] ?? '')),
    [form, baseline, stagedDoc, stagedLogo, logoRemoved],
  );
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  useUnsavedChangesGuard(dirty && !saving);

  // Load server values into the form — but never clobber edits in progress.
  useEffect(() => {
    if (!profile || dirtyRef.current) return;
    const f = fromProfile(profile);
    setForm(f);
    setBaseline(f);
  }, [profile]);

  // Saved logo is private: fetch with the token and show via an object URL.
  useEffect(() => {
    if (!profile?.hasLogo) { setSavedLogoUrl(null); return undefined; }
    let url = null;
    let cancelled = false;
    fetchObjectUrl('/blood-banks/me/logo', store.getState().auth?.accessToken)
      .then((u) => { if (cancelled) URL.revokeObjectURL(u); else { url = u; setSavedLogoUrl(u); } })
      .catch(() => setSavedLogoUrl(null));
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [profile?.hasLogo, profile?.id, store]);

  useEffect(() => {
    if (!stagedLogo) { setStagedLogoUrl(null); return undefined; }
    const u = URL.createObjectURL(stagedLogo);
    setStagedLogoUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [stagedLogo]);

  const hasDocument = !!stagedDoc || !!profile?.hasLicenseDocument;
  const { items, percent } = completion(form, hasDocument);
  const status = profile?.verificationStatus || 'NOT_SUBMITTED';
  const identityChanged = IDENTITY_FIELDS.some((k) => (form[k] ?? '') !== (baseline[k] ?? ''));
  const willReverify = profile && (
    (status === 'VERIFIED' && (identityChanged || stagedDoc))
    || (status === 'REJECTED' && dirty)
    || (status === 'EXPIRED' && stagedDoc)
  );

  // ---------- field handlers ----------

  const setField = (name) => (e) => {
    const next = { ...form, [name]: e.target.value };
    setForm(next);
    // Re-validate fields that already show an error (and the dependent expiry date).
    setErrors((prev) => {
      const out = { ...prev };
      if (out[name]) out[name] = validateField(name, next);
      if (name === 'licenseIssueDate' && out.licenseExpiryDate) out.licenseExpiryDate = validateField('licenseExpiryDate', next);
      Object.keys(out).forEach((k) => { if (!out[k]) delete out[k]; });
      return out;
    });
  };
  const blurField = (name) => () => {
    const msg = validateField(name, form);
    setErrors((prev) => {
      const out = { ...prev };
      if (msg) out[name] = msg; else delete out[name];
      return out;
    });
  };
  const fieldProps = (name, extra = {}) => ({
    id: `bb-${name}`,
    name,
    value: form[name] ?? '',
    onChange: setField(name),
    onBlur: blurField(name),
    error: errors[name],
    disabled: saving,
    ...extra,
  });

  // ---------- geolocation ----------

  const applyCoords = ({ latitude, longitude }) => {
    setForm((f) => ({ ...f, latitude, longitude }));
    setErrors((e) => { const o = { ...e }; delete o.latitude; delete o.longitude; return o; });
    notify.success('Location captured — review it before saving');
  };

  const locateMe = async () => {
    setGeoError('');
    if (!navigator.geolocation) {
      setGeoError('Your browser does not support location access. Enter the coordinates manually.');
      return;
    }
    try {
      const { latitude, longitude } = await geo.request();
      const next = { latitude: latitude.toFixed(7), longitude: longitude.toFixed(7) };
      // Never silently overwrite coordinates that are already set.
      if ((form.latitude ?? '').trim() || (form.longitude ?? '').trim()) setGeoConfirm(next);
      else applyCoords(next);
    } catch (err) {
      setGeoError(GEO_ERRORS[err?.code] || 'Could not get your location. Enter the coordinates manually.');
    }
  };

  // ---------- logo ----------

  const pickLogo = (file) => {
    const msg = validateFile(file, { exts: ['png', 'jpg', 'jpeg'], maxBytes: LOGO_MAX_BYTES, label: 'logo' });
    setLogoError(msg);
    if (!msg) { setStagedLogo(file); setLogoRemoved(false); }
  };

  // ---------- save / cancel ----------

  const resetForm = () => {
    setForm(baseline);
    setErrors({});
    setServerError('');
    setStagedDoc(null);
    setDocUploadError('');
    setDocMissingError('');
    setStagedLogo(null);
    setLogoRemoved(false);
    setLogoError('');
    setGeoError('');
  };

  const focusFirstError = (errs) => {
    const first = Object.keys(EMPTY_FORM).find((k) => errs[k]);
    if (first) document.getElementById(`bb-${first}`)?.focus();
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    setServerError('');
    setDocUploadError('');
    const errs = validateForm(form);
    setErrors(errs);
    setDocMissingError(hasDocument ? '' : 'Upload your license certificate to submit it for verification.');
    if (Object.keys(errs).length) {
      notify.error('Please fix the highlighted fields');
      focusFirstError(errs);
      return;
    }

    setSaving(true);
    const token = () => store.getState().auth?.accessToken;
    try {
      // 1. Profile fields (also creates the profile on first save).
      try {
        await upsert({ resource: RESOURCE, body: toBody(form) }).unwrap();
      } catch (err) {
        const { message, fieldErrors } = parseApiError(err);
        setServerError(message);
        setErrors((prev) => ({ ...prev, ...fieldErrors }));
        focusFirstError(fieldErrors);
        notify.error(message);
        return;
      }
      setBaseline(form);

      // 2. Logo (cosmetic; failure doesn't block the license).
      try {
        if (stagedLogo) await uploadWithProgress('/blood-banks/me/logo', stagedLogo, token());
        else if (logoRemoved) {
          const res = await fetch(`${API_BASE_URL}/blood-banks/me/logo`, {
            method: 'DELETE', headers: { Authorization: `Bearer ${token()}` },
          });
          if (!res.ok) throw new Error('Could not remove the logo');
        }
        setStagedLogo(null);
        setLogoRemoved(false);
      } catch (err) {
        setLogoError(err.message || 'Logo upload failed');
      }

      // 3. License certificate.
      if (stagedDoc) {
        setDocProgress(0);
        try {
          await uploadWithProgress('/blood-banks/me/license-document', stagedDoc, token(), setDocProgress);
          setStagedDoc(null);
          notify.success('Profile saved. Your license was submitted for verification.');
        } catch (err) {
          setDocUploadError(err.message);
          notify.error(`Profile saved, but the license upload failed: ${err.message}`);
          return;
        } finally {
          setDocProgress(null);
        }
      } else {
        notify.success('Profile saved successfully');
      }
    } finally {
      setSaving(false);
      dispatch(apiSlice.util.invalidateTags(['User'])); // refetch server-derived status
    }
  };

  const onCancel = () => (dirty ? setConfirmCancel(true) : null);

  const viewSavedDoc = () => openPrivateFile('/blood-banks/me/license-document', store.getState().auth?.accessToken)
    .catch((err) => notify.error(err.message));

  // ---------- render ----------

  const logoSrc = stagedLogoUrl || (!logoRemoved ? savedLogoUrl : null);
  const displayName = form.bankName.trim() || profile?.bankName || user?.fullName || 'Your blood bank';
  const stateOptions = form.state && !INDIAN_STATES.includes(form.state) ? [form.state, ...INDIAN_STATES] : INDIAN_STATES;
  const mapsQuery = [form.address, form.city, form.state, form.pincode].filter((s) => s?.trim()).join(', ');

  return (
    <div className="mx-auto max-w-6xl pb-4">
      <PageHeader icon={Building2} breadcrumb="Home / Profile" title="Blood Bank Registration"
                  subtitle="Keep your details and license current. Verification is performed by platform administrators." />

      {loadError && (
        <Alert type="error" title="Could not load your profile">
          {parseApiError(error).message} <button type="button" className="font-semibold underline" onClick={refetch}>Retry</button>
        </Alert>
      )}
      {notFound && (
        <Alert type="info" title="Welcome — let's register your blood bank">
          Fill in the required details and upload your license certificate. An administrator will review it before you go live.
        </Alert>
      )}

      {/* ---------- summary card ---------- */}
      <div className="mb-6 overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900">
        <div className="h-20 bg-grad-brand" />
        <div className="flex flex-wrap items-end gap-4 px-6 pb-6">
          <div className="relative -mt-10 shrink-0">
            {logoSrc ? (
              <img src={logoSrc} alt={`${displayName} logo`}
                   className="h-20 w-20 rounded-3xl bg-white object-cover shadow-emergency ring-4 ring-white dark:ring-slate-900" />
            ) : (
              <span className="grid h-20 w-20 place-items-center rounded-3xl bg-grad-brand text-2xl font-extrabold text-white shadow-emergency ring-4 ring-white dark:ring-slate-900" aria-hidden="true">
                {displayName.charAt(0).toUpperCase()}
              </span>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-xl font-bold text-slate-900 dark:text-white">{displayName}</h2>
            <p className="truncate text-sm text-slate-500">{form.contactEmail || user?.email}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {isLoading ? <Skeleton className="h-5 w-32" /> : <VerificationBadge status={status} />}
              {profile?.licenseNumber && <span className="font-mono text-xs text-slate-500">License {profile.licenseNumber}</span>}
            </div>
          </div>
          <div className="grid w-full gap-3 sm:w-64">
            <div>
              <div className="mb-1 flex items-center justify-between text-xs font-medium text-slate-500">
                <span id="completion-label">Profile completeness</span>
                <span className="font-bold text-brand-600">{percent}%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" role="progressbar"
                   aria-labelledby="completion-label" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
                <div className="h-full rounded-full bg-grad-brand transition-all duration-500" style={{ width: `${percent}%` }} />
              </div>
            </div>
            <p className="text-xs text-slate-500">
              {percent === 100 && status !== 'VERIFIED'
                ? 'Profile complete — verification still requires administrator approval.'
                : 'A complete profile does not by itself verify your blood bank.'}
            </p>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">{[...Array(3)].map((_, i) => <Skeleton key={i} className="h-64 rounded-2xl" />)}</div>
          <Skeleton className="h-72 rounded-2xl" />
        </div>
      ) : (
        <form noValidate onSubmit={onSubmit} aria-busy={saving}>
          <div className="grid gap-6 lg:grid-cols-3">
            {/* ---------- main column ---------- */}
            <div className="min-w-0 space-y-6 lg:col-span-2">
              <Alert type="error" title="Could not save">{serverError}</Alert>

              <Section id="basic" icon={Building2} title="Basic Information" description="How your blood bank appears on the network.">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <TextField className="sm:col-span-2" label={<Req>Blood bank name</Req>} autoComplete="organization"
                             aria-required="true" maxLength={100} {...fieldProps('bankName')} />
                  <TextField label="Website" type="url" inputMode="url" placeholder="https://example.org"
                             hint="Optional. Include https://" autoComplete="url" {...fieldProps('website')} />
                  <div>
                    <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Logo</span>
                    <div className="flex flex-wrap items-center gap-2">
                      <label className={`inline-flex cursor-pointer items-center gap-2 rounded-xl border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 transition focus-within:ring-4 focus-within:ring-brand-500/20 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 ${saving ? 'pointer-events-none opacity-60' : ''}`}>
                        <Camera className="h-4 w-4" aria-hidden="true" /> {logoSrc ? 'Change logo' : 'Upload logo'}
                        <input type="file" accept={LOGO_ACCEPT} className="sr-only" disabled={saving}
                               aria-describedby="logo-hint"
                               onChange={(e) => { if (e.target.files?.[0]) pickLogo(e.target.files[0]); e.target.value = ''; }} />
                      </label>
                      {logoSrc && (
                        <Button type="button" size="sm" variant="ghost" disabled={saving} leftIcon={<Trash2 className="h-4 w-4" />}
                                onClick={() => { setStagedLogo(null); setLogoRemoved(!!profile?.hasLogo); setLogoError(''); }}>
                          Remove
                        </Button>
                      )}
                    </div>
                    {logoError
                      ? <span role="alert" className="mt-1.5 block text-xs font-medium text-brand-600">{logoError}</span>
                      : <span id="logo-hint" className="mt-1.5 block text-xs text-slate-500 dark:text-slate-400">Optional. PNG or JPG, max 2 MB{stagedLogo ? ' · uploads when you save' : ''}.</span>}
                  </div>
                </div>
              </Section>

              <Section id="contact" icon={Phone} title="Contact Information" description="Official channels hospitals and administrators will use.">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <TextField className="sm:col-span-2" label={<Req>Official contact email</Req>} type="email" inputMode="email"
                             autoComplete="email" aria-required="true" maxLength={150} {...fieldProps('contactEmail')} />
                  <TextField label={<Req>Official contact phone</Req>} type="tel" inputMode="tel" autoComplete="tel"
                             aria-required="true" maxLength={15} hint="7–15 digits; + - and spaces allowed" {...fieldProps('phone')} />
                  <TextField label="Alternate contact number" type="tel" inputMode="tel" maxLength={15}
                             hint="Optional" {...fieldProps('alternatePhone')} />
                </div>
              </Section>

              <Section id="address" icon={MapPin} title="Address & Location"
                       description="Coordinates power location-based matching with nearby hospitals and patients.">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <TextField className="sm:col-span-2" label={<Req>Full address</Req>} autoComplete="street-address"
                             aria-required="true" maxLength={255} placeholder="Building, street, area" {...fieldProps('address')} />
                  <div>
                    <label htmlFor="bb-state" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200"><Req>State</Req></label>
                    <Select id="bb-state" name="state" value={form.state} onChange={setField('state')} onBlur={blurField('state')}
                            disabled={saving} aria-required="true" aria-invalid={errors.state ? true : undefined}
                            aria-describedby={errors.state ? 'bb-state-desc' : undefined}>
                      <option value="">Select state / UT…</option>
                      {stateOptions.map((s) => <option key={s} value={s}>{s}</option>)}
                    </Select>
                    {errors.state && <span id="bb-state-desc" className="mt-1.5 block text-xs font-medium text-brand-600">{errors.state}</span>}
                  </div>
                  <TextField label={<Req>City</Req>} autoComplete="address-level2" aria-required="true" maxLength={50} {...fieldProps('city')} />
                  <TextField label={<Req>PIN code</Req>} inputMode="numeric" autoComplete="postal-code" aria-required="true"
                             maxLength={6} hint="6 digits" {...fieldProps('pincode')} />
                  <div className="hidden sm:block" aria-hidden="true" />
                  <TextField label={<Req>Latitude</Req>} inputMode="decimal" aria-required="true" placeholder="e.g. 19.0760000"
                             hint="-90 to 90" {...fieldProps('latitude')} />
                  <TextField label={<Req>Longitude</Req>} inputMode="decimal" aria-required="true" placeholder="e.g. 72.8777000"
                             hint="-180 to 180" {...fieldProps('longitude')} />
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <Button type="button" variant="secondary" loading={geo.loading} disabled={saving} onClick={locateMe}
                          leftIcon={<Navigation className="h-4 w-4" />}>
                    Use my current location
                  </Button>
                  {mapsQuery && (
                    <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(mapsQuery)}`}
                       target="_blank" rel="noreferrer"
                       className="inline-flex items-center gap-1 text-sm font-semibold text-ocean-600 hover:underline">
                      Look up address on Google Maps <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                    </a>
                  )}
                </div>
                <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                  Use your device location only while at the blood bank premises. You can always type or correct coordinates manually — location permission is not required.
                </p>
                {geoError && <div className="mt-3"><Alert type="warning">{geoError}</Alert></div>}
              </Section>

              <Section id="license" icon={FileBadge2} title="License & Verification"
                       description="Your license is reviewed by an administrator before you can fulfil requests.">
                {willReverify && (
                  <Alert type="warning" title="This will send your license for review">
                    {status === 'VERIFIED'
                      ? 'You changed your license details or certificate. After saving, your blood bank returns to Pending Verification and cannot fulfil requests until an administrator approves it again.'
                      : 'Saving will resubmit your details to an administrator for verification.'}
                  </Alert>
                )}
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <TextField className="sm:col-span-2" label={<Req>License number</Req>} aria-required="true" maxLength={50}
                             autoComplete="off" hint="Exactly as printed on your certificate" {...fieldProps('licenseNumber')} />
                  <TextField label="License issue date" type="date" max={new Date().toISOString().slice(0, 10)}
                             {...fieldProps('licenseIssueDate')} />
                  <TextField label="License expiry date" type="date" min={form.licenseIssueDate || undefined}
                             {...fieldProps('licenseExpiryDate')} />
                </div>
                <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                  Enter dates where applicable, exactly as printed. Validity rules differ between licensing authorities —
                  leave a date blank if your certificate does not state it. An expired license is never treated as valid.
                </p>

                <fieldset className="mt-5">
                  <legend className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-200">
                    <Req>License certificate</Req>
                  </legend>
                  <LicenseUploader
                    saved={profile?.hasLicenseDocument ? {
                      name: profile.licenseDocumentName, size: profile.licenseDocumentSize, type: profile.licenseDocumentType,
                    } : null}
                    staged={stagedDoc}
                    onStage={(f) => { setStagedDoc(f); setDocUploadError(''); if (f) setDocMissingError(''); }}
                    onViewSaved={viewSavedDoc}
                    progress={docProgress}
                    uploadError={docUploadError}
                    error={docMissingError}
                  />
                </fieldset>
              </Section>
            </div>

            {/* ---------- side column ---------- */}
            <aside className="space-y-6 lg:sticky lg:top-6 lg:self-start" aria-label="Verification and completeness">
              <VerificationPanel profile={profile} />
              <Card className="border border-slate-100 dark:border-slate-800">
                <h2 className="mb-1 text-sm font-bold text-slate-900 dark:text-white">Profile completeness · {percent}%</h2>
                <p className="mb-3 text-xs text-slate-500">Required to submit for verification.</p>
                <ul className="space-y-1.5">
                  {items.map((it) => (
                    <li key={it.key}>
                      <button type="button"
                              onClick={() => document.getElementById(it.key === 'licenseDocument' ? 'license-title' : `bb-${it.key}`)
                                ?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
                              className="flex w-full items-center gap-2 rounded-lg px-1.5 py-1 text-left text-sm hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-brand-500 dark:hover:bg-slate-800">
                        {it.done
                          ? <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                          : <Circle className="h-4 w-4 shrink-0 text-slate-300" aria-hidden="true" />}
                        <span className={it.done ? 'text-slate-600 dark:text-slate-300' : 'font-medium text-slate-900 dark:text-white'}>{it.label}</span>
                        <span className="sr-only">{it.done ? '(complete)' : '(missing)'}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </Card>
            </aside>
          </div>

          {/* ---------- sticky actions ---------- */}
          <div className="sticky bottom-0 z-20 -mx-5 mt-6 border-t border-slate-200 bg-white/90 px-5 py-3 backdrop-blur dark:border-slate-800 dark:bg-slate-950/90 sm:mx-0 sm:rounded-2xl sm:border sm:shadow-soft">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="flex items-center gap-1.5 text-sm text-slate-500" aria-live="polite">
                {dirty
                  ? <><AlertTriangle className="h-4 w-4 text-warning" aria-hidden="true" /> You have unsaved changes</>
                  : profile ? 'All changes saved' : 'Not registered yet'}
              </p>
              <div className="flex gap-2">
                <Button type="button" variant="secondary" disabled={!dirty || saving} onClick={onCancel}>Cancel</Button>
                <Button type="submit" loading={saving} disabled={!dirty && !!profile} leftIcon={<Save className="h-4 w-4" />}>
                  {saving ? (docProgress != null ? `Uploading ${docProgress}%` : 'Saving…') : 'Save Changes'}
                </Button>
              </div>
            </div>
          </div>
        </form>
      )}

      <Modal open={!!geoConfirm} onClose={() => setGeoConfirm(null)} title="Replace saved coordinates?"
             footer={<>
               <Button variant="secondary" onClick={() => setGeoConfirm(null)}>Keep current</Button>
               <Button onClick={() => { applyCoords(geoConfirm); setGeoConfirm(null); }}>Use new location</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">Your device reported a different location. Only replace the coordinates if you are at the blood bank.</p>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-slate-500">Current</dt>
          <dd className="font-mono">{form.latitude || '—'}, {form.longitude || '—'}</dd>
          <dt className="text-slate-500">New</dt>
          <dd className="font-mono">{geoConfirm?.latitude}, {geoConfirm?.longitude}</dd>
        </dl>
      </Modal>

      <Modal open={confirmCancel} onClose={() => setConfirmCancel(false)} title="Discard changes?"
             footer={<>
               <Button variant="secondary" onClick={() => setConfirmCancel(false)}>Keep editing</Button>
               <Button variant="danger" onClick={() => { resetForm(); setConfirmCancel(false); }}>Discard changes</Button>
             </>}>
        <p className="text-sm text-slate-600 dark:text-slate-300">Your unsaved edits and any selected files will be lost.</p>
      </Modal>
    </div>
  );
}
