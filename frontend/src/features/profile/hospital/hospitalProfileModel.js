/**
 * Hospital profile: options, form <-> API mapping, validation and completion.
 * Mirrors the backend (HospitalRequest + HospitalService); error keys match
 * the server's field names so both land on the same input.
 */
import { INDIAN_STATES } from '../donor/donorProfileModel';

export { INDIAN_STATES };

export const CATEGORIES = [
  { value: 'GOVERNMENT_HOSPITAL', label: 'Government hospital' },
  { value: 'PRIVATE_HOSPITAL', label: 'Private hospital' },
  { value: 'NURSING_HOME', label: 'Nursing home' },
  { value: 'TRUST_OR_CHARITABLE', label: 'Trust / charitable hospital' },
  { value: 'MEDICAL_COLLEGE', label: 'Medical college hospital' },
  { value: 'CLINIC', label: 'Clinic' },
  { value: 'OTHER', label: 'Other' },
];

export const BLOOD_BANK_OPTIONS = [
  { value: 'NO', label: 'No — we only request blood', hint: 'No blood bank license is needed.' },
  { value: 'YES', label: 'Yes — we run our own blood bank', hint: 'You will also need to upload a valid blood bank license.' },
];

/** Same rule as HospitalRequest.PHONE: 10-digit landline (with STD code) or mobile, optional +91/91/0. */
const PHONE_RE = /^(?:\+?91[\s-]?|0)?[1-9](?:[\s-]?\d){9}$/;
const PIN_RE = /^[1-9][0-9]{5}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const str = (v) => (v === null || v === undefined ? '' : String(v));

/** Server profile (or null for a first-time hospital) -> form state. */
export function toForm(p) {
  const h = p || {};
  return {
    hospitalName: str(h.hospitalName),
    licenseNumber: str(h.licenseNumber),
    registrationCategory: str(h.registrationCategory),
    // The column defaults to false, so a hospital that never answered (no category yet) must choose explicitly.
    operatesBloodBank: h.registrationCategory ? (h.operatesBloodBank ? 'YES' : 'NO') : '',
    phone: str(h.phone),
    emergencyPhone: str(h.emergencyPhone),
    officialEmail: str(h.officialEmail),
    contactPersonName: str(h.contactPersonName),
    contactPersonDesignation: str(h.contactPersonDesignation),
    address: str(h.address),
    city: str(h.city),
    state: str(h.state),
    pincode: str(h.pincode),
    latitude: str(h.latitude),
    longitude: str(h.longitude),
  };
}

/** Form state -> PUT /hospitals/me body. Blank optional fields become null. */
export function toPayload(f) {
  const text = (s) => (s.trim() === '' ? null : s.trim());
  const num = (s) => (s.trim() === '' ? null : Number(s));
  return {
    hospitalName: f.hospitalName.trim(),
    licenseNumber: f.licenseNumber.trim(),
    registrationCategory: f.registrationCategory || null,
    operatesBloodBank: f.operatesBloodBank === 'YES' ? true : f.operatesBloodBank === 'NO' ? false : null,
    phone: text(f.phone),
    emergencyPhone: text(f.emergencyPhone),
    officialEmail: text(f.officialEmail),
    contactPersonName: text(f.contactPersonName),
    contactPersonDesignation: text(f.contactPersonDesignation),
    address: text(f.address),
    city: text(f.city),
    state: f.state || null,
    pincode: text(f.pincode),
    latitude: num(f.latitude),
    longitude: num(f.longitude),
  };
}

const digitsOf = (s) => s.replace(/\D/g, '').slice(-10);

/** All client-side rules. Returns { fieldName: message } (empty when valid). */
export function validate(f) {
  const e = {};
  const required = (name, label) => { if (!f[name].trim()) e[name] = `${label} is required`; };
  const maxLen = (name, n) => { if (f[name].trim().length > n) e[name] = `Use ${n} characters or fewer`; };

  required('hospitalName', 'Hospital name'); maxLen('hospitalName', 100);
  required('licenseNumber', 'License number'); maxLen('licenseNumber', 50);
  if (!f.registrationCategory) e.registrationCategory = 'Choose your registration category';
  if (!f.operatesBloodBank) e.operatesBloodBank = 'Tell us whether your hospital runs its own blood bank';

  if (!f.phone.trim()) e.phone = 'Phone number is required';
  else if (!PHONE_RE.test(f.phone.trim())) e.phone = 'Enter a 10-digit number, including the STD code for landlines';
  if (f.emergencyPhone.trim() && !PHONE_RE.test(f.emergencyPhone.trim())) {
    e.emergencyPhone = 'Enter a 10-digit number, including the STD code for landlines';
  }
  if (f.officialEmail.trim() && !EMAIL_RE.test(f.officialEmail.trim())) e.officialEmail = 'Enter a valid email address';
  maxLen('officialEmail', 150);
  maxLen('contactPersonName', 100);
  maxLen('contactPersonDesignation', 100);
  if (f.contactPersonDesignation.trim() && !f.contactPersonName.trim()) {
    e.contactPersonName = 'Add the name of the contact person';
  }

  required('address', 'Address'); maxLen('address', 255);
  required('city', 'City'); maxLen('city', 50);
  if (!f.state) e.state = 'State is required';
  if (!f.pincode.trim()) e.pincode = 'PIN code is required';
  else if (!PIN_RE.test(f.pincode.trim())) e.pincode = 'PIN code must be 6 digits and cannot start with 0';

  const lat = f.latitude.trim();
  const lng = f.longitude.trim();
  if (lat || lng) {
    const latN = Number(lat);
    const lngN = Number(lng);
    if (!lat) e.latitude = 'Enter both latitude and longitude, or leave both empty';
    else if (!Number.isFinite(latN) || latN < -90 || latN > 90) e.latitude = 'Latitude must be between -90 and 90';
    if (!lng) e.longitude = 'Enter both latitude and longitude, or leave both empty';
    else if (!Number.isFinite(lngN) || lngN < -180 || lngN > 180) e.longitude = 'Longitude must be between -180 and 180';
  }
  return e;
}

/** True when the 24x7 number is just the main number again (worth a hint, not an error). */
export const sameAsMainPhone = (f) =>
  f.emergencyPhone.trim() !== '' && PHONE_RE.test(f.phone.trim()) && PHONE_RE.test(f.emergencyPhone.trim())
  && digitsOf(f.phone) === digitsOf(f.emergencyPhone);

/** Order fields appear on the page, so "fix the first error" focuses the topmost one. */
export const FIELD_ORDER = [
  'hospitalName', 'licenseNumber', 'registrationCategory', 'operatesBloodBank',
  'phone', 'emergencyPhone', 'officialEmail', 'contactPersonName', 'contactPersonDesignation',
  'address', 'city', 'state', 'pincode', 'latitude', 'longitude',
];

export const fieldId = (name) => `hospital-${name}`;

/**
 * How much of the saved profile is filled in: the details verification needs
 * plus the ones that make coordination work (coordinates, a 24x7 number, a
 * named contact). Separate from document and administrator verification.
 */
export function profileCompletion(p) {
  const h = p || {};
  const items = [
    ['Hospital name', h.hospitalName],
    ['License number', h.licenseNumber],
    ['Registration category', h.registrationCategory],
    ['Phone', h.phone],
    ['Address', h.address],
    ['City', h.city],
    ['State', h.state],
    ['PIN code', h.pincode],
    ['Location coordinates', h.latitude != null && h.longitude != null],
    ['24x7 emergency phone', h.emergencyPhone],
    ['Contact person', h.contactPersonName],
    ['Official email', h.officialEmail],
  ];
  const missing = items.filter(([, v]) => !v).map(([label]) => label);
  return { percent: Math.round(((items.length - missing.length) / items.length) * 100), missing };
}
