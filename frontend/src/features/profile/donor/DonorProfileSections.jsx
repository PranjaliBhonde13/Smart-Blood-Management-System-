import { useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  UserRound, MapPin, Navigation, Search, History, SlidersHorizontal, HeartPulse, Lock, Camera, Trash2, X,
} from 'lucide-react';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import RadioGroup from '../../../components/ui/RadioGroup';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import Skeleton from '../../../components/ui/Skeleton';
import { FULL, Label, Section } from '../FormSection';
import { Badge, StatusBadge } from '../../../components/ui/Badge';
import { formatDate, formatDateTime } from '../../../lib/format';
import {
  BLOOD_GROUPS, GENDERS, INDIAN_STATES, AVAILABILITY_OPTIONS, CONTACT_METHODS, YES_NO, SCREENING_QUESTIONS,
  MIN_DONOR_AGE, ageFromDob, todayIso, yearsAgoIso, fieldId, screeningReviewItems,
} from './donorProfileModel';

const PHOTO_TYPES = ['image/png', 'image/jpeg'];
const PHOTO_MAX_BYTES = 2 * 1024 * 1024;

const full = FULL;

// ---------------------------------------------------------------------------

export function PersonalSection({ form, bind, profile, user, photo }) {
  const age = ageFromDob(form.dateOfBirth);
  const email = profile?.email ?? user?.email;
  const emailVerified = profile ? profile.emailVerified : user?.emailVerified;

  return (
    <Section id="personal" icon={UserRound} title="Personal information"
             description="Used to identify you when you donate. Your age is worked out from your date of birth.">
      <PhotoField photo={photo} disabled={!profile} name={form.fullName} />

      <TextField {...bind('fullName')} label={<Label required>Full name</Label>} autoComplete="name" required maxLength={100} />
      <TextField {...bind('dateOfBirth')} type="date" label={<Label required>Date of birth</Label>} required
                 min="1900-01-01" max={yearsAgoIso(MIN_DONOR_AGE)} autoComplete="bday"
                 hint={age != null && age >= 0 ? `Age ${age}` : `You must be at least ${MIN_DONOR_AGE}.`} />

      <Select {...bind('bloodGroup')} label={<Label required>Blood group</Label>} required>
        <option value="">Select…</option>
        {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
      </Select>
      <Select {...bind('gender')} label={<Label optional>Gender</Label>}>
        <option value="">Not specified</option>
        {GENDERS.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
      </Select>

      <TextField {...bind('phone')} type="tel" inputMode="tel" autoComplete="tel" required
                 label={<Label required>Mobile number</Label>} placeholder="98765 43210"
                 hint="Not verified — SMS verification isn't available yet, so please double-check this number." />
      <TextField id={fieldId('email')} type="email" label="Email" value={email || ''} readOnly
                 hint={`${emailVerified ? 'Verified' : 'Not verified'} · This is your sign-in email and can't be changed here.`} />

      <TextField {...bind('alternatePhone')} type="tel" inputMode="tel" label={<Label optional>Alternate mobile number</Label>} />
      <div className="hidden md:block" />
      <TextField {...bind('emergencyContactName')} label={<Label optional>Emergency contact name</Label>} maxLength={100} />
      <TextField {...bind('emergencyContactPhone')} type="tel" inputMode="tel" label={<Label optional>Emergency contact number</Label>} />
    </Section>
  );
}

function PhotoField({ photo, disabled, name }) {
  const input = useRef(null);
  const onPick = (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow picking the same file again
    if (!file) return;
    if (!PHOTO_TYPES.includes(file.type)) return photo.onError('Choose a PNG or JPG image.');
    if (file.size > PHOTO_MAX_BYTES) return photo.onError('Choose an image smaller than 2 MB.');
    photo.onUpload(file);
  };
  const initial = name?.trim()?.charAt(0)?.toUpperCase() || '?';

  return (
    <div className={`${full} flex flex-wrap items-center gap-4`}>
      {photo.url ? (
        <img src={photo.url} alt="Your profile photo" className="h-16 w-16 rounded-2xl object-cover ring-1 ring-slate-200 dark:ring-slate-700" />
      ) : (
        <span className="grid h-16 w-16 place-items-center rounded-2xl bg-slate-100 text-xl font-bold text-slate-400 dark:bg-slate-800" aria-hidden="true">{initial}</span>
      )}
      <div>
        <p className="text-sm font-medium text-slate-700 dark:text-slate-200"><Label optional>Profile photo</Label></p>
        <p id="donor-photo-hint" className="text-xs text-slate-500 dark:text-slate-400">
          {disabled ? 'Save your profile first to add a photo.' : 'PNG or JPG, up to 2 MB. Only you can see it.'}
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          <input ref={input} type="file" accept="image/png,image/jpeg" className="sr-only" tabIndex={-1}
                 aria-hidden="true" onChange={onPick} />
          <Button type="button" size="sm" variant="secondary" disabled={disabled} loading={photo.uploading}
                  aria-describedby="donor-photo-hint" onClick={() => input.current?.click()}
                  leftIcon={<Camera className="h-4 w-4" />}>
            {photo.url ? 'Change photo' : 'Upload photo'}
          </Button>
          {photo.url && (
            <Button type="button" size="sm" variant="ghost" loading={photo.removing} onClick={photo.onRemove}
                    leftIcon={<Trash2 className="h-4 w-4" />}>Remove</Button>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

export function LocationSection({ form, bind, set, geo, onUseLocation, maps, onGeocode, geocoding, locationNote }) {
  const states = form.state && !INDIAN_STATES.includes(form.state) ? [form.state, ...INDIAN_STATES] : INDIAN_STATES;
  const hasCoords = form.latitude.trim() !== '' || form.longitude.trim() !== '';

  return (
    <Section id="location" icon={MapPin} title="Address & location"
             description="Your city, state and PIN code are required. Your exact address is never shown to hospitals or patients.">
      <TextField {...bind('address')} className={full} label="Full address" autoComplete="street-address"
                 placeholder="House / flat, street, area" maxLength={255} />
      <TextField {...bind('city')} label={<Label required>City</Label>} autoComplete="address-level2" required maxLength={50} />
      <Select {...bind('state')} label={<Label required>State / union territory</Label>} required>
        <option value="">Select…</option>
        {states.map((s) => <option key={s} value={s}>{s}</option>)}
      </Select>
      <TextField {...bind('pincode')} label={<Label required>PIN code</Label>} inputMode="numeric" autoComplete="postal-code"
                 required maxLength={6} placeholder="411001" />
      <div className="hidden md:block" />

      <div className={`${full} rounded-2xl border border-slate-200 p-4 dark:border-slate-700`}>
        <p className="text-sm font-medium text-slate-700 dark:text-slate-200">Location for nearby matching</p>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Coordinates are needed only for location-based matching: alerts about requests near you, and appearing
          (at an approximate position) in nearby-donor searches. Without them you can still receive requests that
          have no location and browse open requests at any time.
        </p>

        {geo.permission === 'denied' && (
          <div className="mt-3"><Alert type="warning">
            Location access is blocked for this site. You can allow it in your browser settings, or type your coordinates below.
          </Alert></div>
        )}
        {geo.error && <div className="mt-3"><Alert type="error">{geo.error}</Alert></div>}
        {locationNote && <div className="mt-3"><Alert type={locationNote.type}>{locationNote.text}</Alert></div>}

        <div className="mt-3 flex flex-wrap gap-2">
          <Button type="button" variant="secondary" size="sm" loading={geo.loading} onClick={onUseLocation}
                  leftIcon={<Navigation className="h-4 w-4" />}>Use my current location</Button>
          {maps === 'ready' && (
            <Button type="button" variant="secondary" size="sm" loading={geocoding} onClick={onGeocode}
                    leftIcon={<Search className="h-4 w-4" />}>Find from my address</Button>
          )}
          {hasCoords && (
            <Button type="button" variant="ghost" size="sm" onClick={() => { set('latitude', ''); set('longitude', ''); }}
                    leftIcon={<X className="h-4 w-4" />}>Clear coordinates</Button>
          )}
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField {...bind('latitude')} label="Latitude" inputMode="decimal" placeholder="18.5204" hint="Between -90 and 90" />
          <TextField {...bind('longitude')} label="Longitude" inputMode="decimal" placeholder="73.8567" hint="Between -180 and 180" />
        </div>
      </div>
    </Section>
  );
}

// ---------------------------------------------------------------------------

export function HistorySection({ form, bindRadio, bind, donations }) {
  const rows = donations.data?.data || [];
  return (
    <Section id="history" icon={History} title="Donation history"
             description="Tell us about donations made anywhere. Donations completed through this network are recorded automatically.">
      <RadioGroup {...bindRadio('hasDonatedBefore')} className={full} legend="Have you donated blood before?" options={YES_NO} />
      {form.hasDonatedBefore === 'YES' && (
        <TextField {...bind('lastDonationDate')} type="date" label={<Label required>Last donation date</Label>} required
                   min={form.dateOfBirth || '1900-01-01'} max={todayIso()} hint="An approximate date is fine." />
      )}

      <div className={full}>
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">Recorded on this network</h3>
          {rows.length > 0 && (
            <Link to="/donor/donations" className="text-sm font-semibold text-brand-600 hover:underline">View full history</Link>
          )}
        </div>
        {donations.isLoading ? (
          <Skeleton className="h-16" />
        ) : donations.isError ? (
          <p className="text-sm text-slate-500">Your donation history couldn't be loaded right now.</p>
        ) : rows.length === 0 ? (
          <p className="text-sm text-slate-500">No donations recorded through the network yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-100 dark:divide-slate-800 dark:border-slate-800">
            {rows.slice(0, 5).map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
                <span className="text-slate-700 dark:text-slate-200">
                  Request #{d.requestId} · {d.unitsDonated} unit{d.unitsDonated === 1 ? '' : 's'}
                  <span className="ml-2 text-slate-400">{formatDate(d.donationDate || d.createdAt)}</span>
                </span>
                <StatusBadge status={d.status} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Section>
  );
}

// ---------------------------------------------------------------------------

export function PreferencesSection({ bindRadio, profile }) {
  const consentOptions = [
    { value: 'YES', label: 'Yes, send me donation requests',
      hint: 'Requests for blood groups you can donate to, including ones near you if you add coordinates.' },
    { value: 'NO', label: "No, don't send me donation requests",
      hint: "You won't get alerts or appear in nearby-donor searches. You can still browse open requests." },
  ];
  return (
    <Section id="preferences" icon={SlidersHorizontal} title="Donation preferences"
             description="Control when and how you're asked to help. You can change these at any time.">
      <RadioGroup {...bindRadio('availability')} className={full} legend={<Label required>Availability</Label>}
                  options={AVAILABILITY_OPTIONS} required />
      <RadioGroup {...bindRadio('preferredContactMethod')} className={full} legend="Preferred contact method"
                  options={CONTACT_METHODS} columns={3}
                  hint="Request alerts currently arrive in the app and by email. We save your preference for when more channels are added." />
      <RadioGroup {...bindRadio('contactConsent')} className={full} columns={1} required
                  legend={<Label required>Do you agree to receive donation requests?</Label>} options={consentOptions}
                  hint={profile?.contactConsentUpdatedAt ? `Last changed ${formatDateTime(profile.contactConsentUpdatedAt)}` : undefined} />
    </Section>
  );
}

// ---------------------------------------------------------------------------

export function HealthSection({ form, bindRadio, setAnswer, setDeclaration, clearScreening, started, error, profile }) {
  const review = screeningReviewItems(form.screening);
  const savedAt = profile?.healthScreening?.screenedAt;
  const answerOptions = (q) => (q.allowNotApplicable ? [...YES_NO, { value: 'NOT_APPLICABLE', label: 'Not applicable' }] : YES_NO);

  return (
    <Section id="health" icon={HeartPulse} title="Health questionnaire"
             description="Optional. A few quick questions to help you prepare for screening at the blood centre."
             aside={savedAt ? <Badge tone="neutral">Last updated {formatDate(savedAt)}</Badge> : null}>
      <div className={full}>
        <Alert type="info" title="This is not a medical assessment">
          Your answers don't decide whether you can donate. Qualified staff at the blood centre confirm eligibility
          under the applicable guidelines before every donation.
        </Alert>
        <p className="flex items-start gap-2 text-xs text-slate-500 dark:text-slate-400">
          <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          Only you can see these answers. They aren't shared with hospitals or used to match you with requests, and you can clear them at any time.
        </p>
      </div>

      {SCREENING_QUESTIONS.map((q) => (
        <RadioGroup key={q.name} {...bindRadio(`healthScreening.${q.name}`)} className={full}
                    legend={q.label} options={answerOptions(q)} columns={q.allowNotApplicable ? 3 : 2}
                    value={form.screening[q.name]} onChange={(v) => setAnswer(q.name, v)} />
      ))}

      {review.length > 0 && (
        <div className={full}>
          <Alert type="info" title="Worth mentioning at the blood centre">
            Some of your answers are things staff usually ask about. This doesn't mean you can't donate — they'll advise you.
          </Alert>
        </div>
      )}

      {started && (
        <div className={`${full} flex flex-wrap items-start justify-between gap-3`}>
          <Checkbox id={fieldId('healthScreening.declarationAccepted')} checked={form.declarationAccepted}
                    onChange={setDeclaration} error={error('healthScreening.declarationAccepted')} required
                    label={<>I confirm these answers are accurate to the best of my knowledge.<span className="ml-0.5 text-brand-600" aria-hidden="true">*</span></>} />
          <Button type="button" size="sm" variant="ghost" onClick={clearScreening} leftIcon={<Trash2 className="h-4 w-4" />}>
            Clear my answers
          </Button>
        </div>
      )}
    </Section>
  );
}
