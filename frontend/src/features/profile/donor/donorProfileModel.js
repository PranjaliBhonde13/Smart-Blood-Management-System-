/**
 * Donor profile: options, form <-> API mapping, validation and completion.
 * Pure functions only, so the rules are easy to read next to the backend's
 * (DonorRequest bean validation + DonorService.validate). Error keys match the
 * server's field names so server and client errors land on the same field.
 */

export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

export const GENDERS = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'OTHER', label: 'Other' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
];

export const INDIAN_STATES = [
  'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur',
  'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana',
  'Tripura', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
  // Union territories
  'Andaman and Nicobar Islands', 'Chandigarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Delhi',
  'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry',
];

export const AVAILABILITY_OPTIONS = [
  { value: 'AVAILABLE', label: 'Available', hint: 'You can receive requests that match your blood group.' },
  { value: 'UNAVAILABLE', label: 'Temporarily unavailable', hint: "You won't receive requests until you switch back." },
];

export const CONTACT_METHODS = [
  { value: 'PHONE_CALL', label: 'Phone call' },
  { value: 'SMS', label: 'SMS' },
  { value: 'EMAIL', label: 'Email' },
];

export const YES_NO = [
  { value: 'YES', label: 'Yes' },
  { value: 'NO', label: 'No' },
];

/**
 * Self-declared screening questions. `review` is the answer staff may want to
 * talk through; it never blocks anything and is not an eligibility result.
 */
export const SCREENING_QUESTIONS = [
  { name: 'feelingWell', label: 'Are you feeling healthy and well today?', review: 'NO' },
  { name: 'recentIllness', label: 'Have you recently had a fever, cold, flu or any other infection?', review: 'YES' },
  { name: 'currentMedication', label: 'Are you currently taking any medication, including antibiotics or blood thinners?', review: 'YES' },
  { name: 'recentProcedure', label: 'In the past 12 months, have you had a tattoo, body piercing, surgery or a blood transfusion?', review: 'YES' },
  { name: 'chronicCondition', label: 'Do you have a long-term medical condition (for example heart, lung, kidney or liver disease, diabetes, epilepsy or a bleeding disorder)?', review: 'YES' },
  { name: 'pregnancy', label: 'Are you currently pregnant or breastfeeding, or have you been pregnant in the past 12 months?', review: 'YES', allowNotApplicable: true },
];

export const MIN_DONOR_AGE = 18;
const EARLIEST_BIRTH_DATE = '1900-01-01';

/** Same rule as DonorRequest.PHONE on the server: 10-digit Indian mobile, optional +91/91/0 prefix. */
const PHONE_RE = /^(?:\+?91|0)?[\s-]*[6-9](?:[\s-]*\d){9}$/;
const PIN_RE = /^[1-9][0-9]{5}$/;
const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

// ---------- dates ----------

/** Today's date in the user's time zone as YYYY-MM-DD (what <input type="date"> uses). */
export function todayIso(now = new Date()) {
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** YYYY-MM-DD for `years` before today (used for date-input bounds). */
export function yearsAgoIso(years, today = todayIso()) {
  const [y, m, d] = today.split('-');
  return `${String(Number(y) - years).padStart(4, '0')}-${m}-${m === '02' && d === '29' ? '28' : d}`;
}

function isValidIsoDate(s) {
  if (!ISO_DATE_RE.test(s)) return false;
  const [y, m, d] = s.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Whole years between a YYYY-MM-DD birth date and today, or null if invalid. */
export function ageFromDob(dob, today = todayIso()) {
  if (!dob || !isValidIsoDate(dob)) return null;
  const [by, bm, bd] = dob.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0);
}

// ---------- form mapping ----------

const toYesNo = (b) => (b === true ? 'YES' : b === false ? 'NO' : '');
const fromYesNo = (s) => (s === 'YES' ? true : s === 'NO' ? false : null);
const str = (v) => (v === null || v === undefined ? '' : String(v));

export const EMPTY_SCREENING = Object.fromEntries(SCREENING_QUESTIONS.map((q) => [q.name, '']));

/**
 * Server profile (or null for a first-time donor) -> form state. Account
 * fields fall back to the signed-in user so a new donor starts prefilled.
 */
export function toForm(profile, user) {
  const p = profile || {};
  const screening = p.healthScreening;
  return {
    fullName: str(p.fullName ?? user?.fullName),
    dateOfBirth: str(p.dateOfBirth),
    bloodGroup: str(p.bloodGroup),
    phone: str(p.phone),
    gender: str(p.gender),
    address: str(p.address),
    city: str(p.city),
    state: str(p.state),
    pincode: str(p.pincode),
    latitude: str(p.latitude),
    longitude: str(p.longitude),
    hasDonatedBefore: toYesNo(p.hasDonatedBefore),
    lastDonationDate: str(p.lastDonationDate),
    availability: str(p.availability),
    preferredContactMethod: str(p.preferredContactMethod),
    contactConsent: toYesNo(p.contactConsent),
    alternatePhone: str(p.alternatePhone),
    emergencyContactName: str(p.emergencyContactName),
    emergencyContactPhone: str(p.emergencyContactPhone),
    screening: screening
      ? Object.fromEntries(SCREENING_QUESTIONS.map((q) => [q.name, str(screening[q.name])]))
      : { ...EMPTY_SCREENING },
    // Saved answers were confirmed when they were saved; any edit asks again.
    declarationAccepted: Boolean(screening),
  };
}

export const screeningStarted = (form) =>
  SCREENING_QUESTIONS.some((q) => form.screening[q.name] !== '') || form.declarationAccepted;

/** Form state -> PUT /donors/me body. Blank optional fields become null (the PUT replaces the profile). */
export function toPayload(form) {
  const text = (s) => (s.trim() === '' ? null : s.trim());
  const num = (s) => (s.trim() === '' ? null : Number(s));
  return {
    fullName: form.fullName.trim(),
    dateOfBirth: form.dateOfBirth || null,
    bloodGroup: form.bloodGroup || null,
    phone: text(form.phone),
    gender: form.gender || null,
    address: text(form.address),
    city: text(form.city),
    state: form.state || null,
    pincode: text(form.pincode),
    latitude: num(form.latitude),
    longitude: num(form.longitude),
    hasDonatedBefore: fromYesNo(form.hasDonatedBefore),
    lastDonationDate: form.hasDonatedBefore === 'YES' ? form.lastDonationDate || null : null,
    availability: form.availability || null,
    preferredContactMethod: form.preferredContactMethod || null,
    contactConsent: fromYesNo(form.contactConsent),
    alternatePhone: text(form.alternatePhone),
    emergencyContactName: text(form.emergencyContactName),
    emergencyContactPhone: text(form.emergencyContactPhone),
    healthScreening: screeningStarted(form)
      ? {
          ...Object.fromEntries(SCREENING_QUESTIONS.map((q) => [q.name, form.screening[q.name] || null])),
          declarationAccepted: form.declarationAccepted,
        }
      : null,
  };
}

// ---------- validation ----------

const digitsOf = (s) => s.replace(/\D/g, '').slice(-10);

/** All client-side rules. Returns { fieldName: message } (empty when valid). */
export function validate(form, today = todayIso()) {
  const e = {};
  const required = (name, label) => { if (!String(form[name]).trim()) e[name] = `${label} is required`; };

  required('fullName', 'Full name');
  if (form.fullName.trim().length > 100) e.fullName = 'Use 100 characters or fewer';

  if (!form.dateOfBirth) e.dateOfBirth = 'Date of birth is required';
  else if (!isValidIsoDate(form.dateOfBirth)) e.dateOfBirth = 'Enter a valid date';
  else if (form.dateOfBirth > today) e.dateOfBirth = 'Date of birth must be in the past';
  else if (form.dateOfBirth < EARLIEST_BIRTH_DATE) e.dateOfBirth = 'Enter a valid date of birth';
  else if (ageFromDob(form.dateOfBirth, today) < MIN_DONOR_AGE) e.dateOfBirth = `You must be at least ${MIN_DONOR_AGE} to register as a donor`;

  required('bloodGroup', 'Blood group');

  if (!form.phone.trim()) e.phone = 'Mobile number is required';
  else if (!PHONE_RE.test(form.phone.trim())) e.phone = 'Enter a valid 10-digit Indian mobile number';

  if (form.address.trim().length > 255) e.address = 'Use 255 characters or fewer';
  required('city', 'City');
  if (form.city.trim().length > 50) e.city = 'Use 50 characters or fewer';
  required('state', 'State');
  if (!form.pincode.trim()) e.pincode = 'PIN code is required';
  else if (!PIN_RE.test(form.pincode.trim())) e.pincode = 'PIN code must be 6 digits and cannot start with 0';

  const lat = form.latitude.trim();
  const lng = form.longitude.trim();
  if (lat || lng) {
    const latN = Number(lat);
    const lngN = Number(lng);
    if (!lat) e.latitude = 'Enter both latitude and longitude, or leave both empty';
    else if (!Number.isFinite(latN) || latN < -90 || latN > 90) e.latitude = 'Latitude must be between -90 and 90';
    if (!lng) e.longitude = 'Enter both latitude and longitude, or leave both empty';
    else if (!Number.isFinite(lngN) || lngN < -180 || lngN > 180) e.longitude = 'Longitude must be between -180 and 180';
  }

  if (form.hasDonatedBefore === 'YES') {
    const last = form.lastDonationDate;
    if (!last) e.lastDonationDate = 'Enter your last donation date (an approximate date is fine)';
    else if (!isValidIsoDate(last)) e.lastDonationDate = 'Enter a valid date';
    else if (last > today) e.lastDonationDate = 'Last donation date cannot be in the future';
    else if (form.dateOfBirth && isValidIsoDate(form.dateOfBirth) && last <= form.dateOfBirth) {
      e.lastDonationDate = 'Last donation date must be after your date of birth';
    }
  }

  if (!form.availability) e.availability = 'Choose your availability';
  if (!form.contactConsent) e.contactConsent = 'Choose whether you want to receive donation requests';

  for (const name of ['alternatePhone', 'emergencyContactPhone']) {
    const v = form[name].trim();
    if (v && !PHONE_RE.test(v)) e[name] = 'Enter a valid 10-digit Indian mobile number';
  }
  if (!e.alternatePhone && form.alternatePhone.trim() && PHONE_RE.test(form.phone.trim())
      && digitsOf(form.alternatePhone) === digitsOf(form.phone)) {
    e.alternatePhone = 'Use a different number from your main mobile number';
  }
  if (form.emergencyContactName.trim().length > 100) e.emergencyContactName = 'Use 100 characters or fewer';
  if (form.emergencyContactPhone.trim() && !form.emergencyContactName.trim()) {
    e.emergencyContactName = 'Add a name for your emergency contact';
  }
  if (form.emergencyContactName.trim() && !form.emergencyContactPhone.trim()) {
    e.emergencyContactPhone = 'Add a number for your emergency contact';
  }

  if (screeningStarted(form)) {
    for (const q of SCREENING_QUESTIONS) {
      if (!form.screening[q.name]) e[`healthScreening.${q.name}`] = 'Please answer this question';
    }
    if (!form.declarationAccepted) e['healthScreening.declarationAccepted'] = 'Please confirm your answers are accurate';
  }
  return e;
}

/** Order fields appear on the page, so "fix the first error" focuses the topmost one. */
export const FIELD_ORDER = [
  'fullName', 'dateOfBirth', 'bloodGroup', 'phone', 'gender', 'alternatePhone',
  'emergencyContactName', 'emergencyContactPhone',
  'address', 'city', 'state', 'pincode', 'latitude', 'longitude',
  'hasDonatedBefore', 'lastDonationDate',
  'availability', 'preferredContactMethod', 'contactConsent',
  ...SCREENING_QUESTIONS.map((q) => `healthScreening.${q.name}`), 'healthScreening.declarationAccepted',
];

/** DOM id for a field (dots are awkward in ids). */
export const fieldId = (name) => `donor-${name.replace(/\./g, '-')}`;

// ---------- completion & review notes ----------

/**
 * How much of the saved profile is filled in. Required details plus the
 * details that make matching work well. Optional personal details (gender,
 * photo, extra contacts) never count, so leaving them out costs nothing. This
 * says nothing about eligibility or verification.
 */
export function profileCompletion(profile) {
  const p = profile || {};
  const items = [
    ['Full name', p.fullName],
    ['Date of birth', p.dateOfBirth],
    ['Blood group', p.bloodGroup],
    ['Mobile number', p.phone],
    ['City', p.city],
    ['State', p.state],
    ['PIN code', p.pincode],
    ['Availability', p.availability],
    ['Consent to be contacted', p.contactConsent != null],
    ['Full address', p.address],
    ['Location coordinates', p.latitude != null && p.longitude != null],
    ['Donation history', p.hasDonatedBefore != null],
    ['Preferred contact method', p.preferredContactMethod],
    ['Health questionnaire', p.healthScreening],
  ];
  const missing = items.filter(([, v]) => !v).map(([label]) => label);
  return { percent: Math.round(((items.length - missing.length) / items.length) * 100), missing };
}

/** Questions whose answer staff may want to discuss. Informational only. */
export function screeningReviewItems(screening) {
  return SCREENING_QUESTIONS.filter((q) => screening[q.name] === q.review);
}
