/**
 * Patient profile form model: empty state, API mapping, client validation
 * (mirrors PatientRequest on the server, which stays the authority), document
 * type catalogue and status display metadata. Verification is never computed
 * here — statuses always come from the API response.
 */
import { BLOOD_GROUPS, INDIAN_STATES, todayIso } from '../donor/donorProfileModel';

export { BLOOD_GROUPS, INDIAN_STATES, todayIso };

export const GENDERS = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'OTHER', label: 'Other' },
  { value: 'UNSPECIFIED', label: 'Prefer not to say' },
];

/** Must match PatientDocumentType on the server. No ID is mandatory; ID numbers are never collected. */
export const IDENTITY_TYPES = [
  { value: 'AADHAAR', label: 'Aadhaar (masked copy recommended)' },
  { value: 'PASSPORT', label: 'Passport' },
  { value: 'VOTER_ID', label: 'Voter ID (EPIC)' },
  { value: 'DRIVING_LICENCE', label: 'Driving licence' },
  { value: 'PAN_CARD', label: 'PAN card' },
  { value: 'OTHER_GOVERNMENT_ID', label: 'Other government-issued photo ID' },
];
export const BLOOD_GROUP_DOC_TYPES = [
  { value: 'LAB_REPORT', label: 'Blood-group laboratory report' },
  { value: 'HOSPITAL_RECORD', label: 'Hospital / authorised medical record' },
  { value: 'BLOOD_GROUP_CARD', label: 'Blood-group card issued by a lab or blood bank' },
];

export const DOC_EXTS = ['pdf', 'jpg', 'jpeg', 'png'];
export const DOC_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';
/** Display only — the server limit (app.patient-documents.max-file-size) is authoritative. */
export const DOC_MAX_BYTES = 5 * 1024 * 1024;

export const EMPTY_FORM = {
  fullName: '', dateOfBirth: '', bloodGroup: '', gender: '', phone: '',
  address: '', city: '', state: '', pincode: '', latitude: '', longitude: '',
  emergencyContactName: '', emergencyContactPhone: '',
  notificationConsent: '', privacyAcknowledged: false,
};

const str = (v) => (v == null ? '' : String(v));
const localPhone = (v) => (v ? String(v).replace(/^\+91/, '') : '');

export function toForm(p, user) {
  if (!p) return { ...EMPTY_FORM, fullName: str(user?.fullName), phone: localPhone(user?.phone) };
  return {
    fullName: str(p.fullName), dateOfBirth: str(p.dateOfBirth), bloodGroup: str(p.bloodGroup),
    gender: str(p.gender), phone: localPhone(p.phone),
    address: str(p.address), city: str(p.city), state: str(p.state), pincode: str(p.pincode),
    latitude: str(p.latitude), longitude: str(p.longitude),
    emergencyContactName: str(p.emergencyContactName), emergencyContactPhone: localPhone(p.emergencyContactPhone),
    notificationConsent: p.notificationConsent == null ? '' : p.notificationConsent ? 'YES' : 'NO',
    privacyAcknowledged: !!p.privacyAcknowledgedAt,
  };
}

export function toPayload(f) {
  const t = (v) => (v ?? '').trim();
  const opt = (v) => (t(v) === '' ? null : t(v));
  return {
    fullName: t(f.fullName), dateOfBirth: opt(f.dateOfBirth), bloodGroup: t(f.bloodGroup),
    gender: opt(f.gender), phone: t(f.phone),
    address: t(f.address), city: t(f.city), state: t(f.state), pincode: t(f.pincode),
    latitude: t(f.latitude) === '' ? null : Number(f.latitude),
    longitude: t(f.longitude) === '' ? null : Number(f.longitude),
    emergencyContactName: opt(f.emergencyContactName), emergencyContactPhone: opt(f.emergencyContactPhone),
    notificationConsent: f.notificationConsent === '' ? null : f.notificationConsent === 'YES',
    privacyAcknowledged: !!f.privacyAcknowledged,
  };
}

const PHONE = /^(\+91[- ]?)?[6-9]\d{9}$/;
const PIN = /^[1-9]\d{5}$/;
const NUM = /^-?\d+(\.\d+)?$/;
const digits = (v) => (v || '').replace(/\D/g, '').slice(-10);

/** Returns { field: message } for invalid fields only. */
export function validate(f, today = todayIso()) {
  const e = {};
  const t = (k) => (f[k] ?? '').trim();
  if (!t('fullName')) e.fullName = 'Full name is required';
  else if (t('fullName').length > 100) e.fullName = 'Use 100 characters or fewer';
  if (!t('dateOfBirth')) e.dateOfBirth = 'Date of birth is required';
  else if (t('dateOfBirth') >= today) e.dateOfBirth = 'Date of birth must be in the past';
  else if (Number(today.slice(0, 4)) - Number(t('dateOfBirth').slice(0, 4)) > 120) e.dateOfBirth = 'Enter a valid date of birth';
  if (!t('bloodGroup')) e.bloodGroup = 'Blood group is required';
  if (!t('phone')) e.phone = 'Mobile number is required';
  else if (!PHONE.test(t('phone').replace(/\s/g, ''))) e.phone = 'Enter a valid 10-digit Indian mobile number';
  if (!t('address')) e.address = 'Full address is required';
  if (!t('city')) e.city = 'City is required';
  if (!t('state')) e.state = 'State is required';
  if (!t('pincode')) e.pincode = 'PIN code is required';
  else if (!PIN.test(t('pincode'))) e.pincode = 'PIN code must be 6 digits and cannot start with 0';
  const lat = t('latitude');
  const lng = t('longitude');
  if (lat && (!NUM.test(lat) || Math.abs(Number(lat)) > 90)) e.latitude = 'Latitude must be between -90 and 90';
  if (lng && (!NUM.test(lng) || Math.abs(Number(lng)) > 180)) e.longitude = 'Longitude must be between -180 and 180';
  if (!e.latitude && !e.longitude && (!!lat !== !!lng)) e[lat ? 'longitude' : 'latitude'] = 'Enter both latitude and longitude, or neither';
  const en = t('emergencyContactName');
  const ep = t('emergencyContactPhone');
  if (en.length > 100) e.emergencyContactName = 'Use 100 characters or fewer';
  if (ep && !PHONE.test(ep.replace(/\s/g, ''))) e.emergencyContactPhone = 'Enter a valid 10-digit Indian mobile number';
  else if (!!en !== !!ep) e[en ? 'emergencyContactPhone' : 'emergencyContactName'] = "Enter both the contact's name and phone, or neither";
  else if (ep && digits(ep) === digits(t('phone'))) e.emergencyContactPhone = 'Use a different number from your own';
  return e;
}

export const FIELD_ORDER = Object.keys(EMPTY_FORM);
export const fieldId = (name) => `patient-${name}`;

/** Required items for the completeness bar (both documents included). Display only. */
export function completion(form, profile) {
  const items = [
    ['fullName', 'Full name'], ['dateOfBirth', 'Date of birth'], ['bloodGroup', 'Blood group'], ['phone', 'Mobile number'],
    ['address', 'Address'], ['city', 'City'], ['state', 'State'], ['pincode', 'PIN code'],
  ].map(([key, label]) => ({ key, label, done: !!(form[key] ?? '').trim() && !validate(form)[key] }));
  items.push({ key: 'identityDoc', label: 'Identity document', done: !!profile?.identity?.document });
  items.push({ key: 'bloodDoc', label: 'Blood-group document', done: !!profile?.bloodGroupVerification?.document });
  return { items, percent: Math.round((items.filter((i) => i.done).length / items.length) * 100) };
}

/** Overall status (server-derived) → label/tone/explanation. */
export const OVERALL_META = {
  PROFILE_INCOMPLETE: { label: 'Profile incomplete', tone: 'neutral', text: 'Complete the required details and upload both documents to request verification.' },
  PENDING_VERIFICATION: { label: 'Pending verification', tone: 'warning', text: 'An administrator is reviewing your documents.' },
  VERIFIED: { label: 'Verified', tone: 'success', text: 'Your identity and blood group are both verified.' },
  REJECTED: { label: 'Rejected', tone: 'brand', text: 'A document was rejected. Read the remarks and upload a corrected document.' },
  ACTION_REQUIRED: { label: 'Action required', tone: 'warning', text: 'An administrator asked for a corrected document. Read the remarks below.' },
};

/** Per-track status (server-derived). */
export const TRACK_META = {
  NOT_SUBMITTED: { label: 'Not submitted', tone: 'neutral' },
  PENDING: { label: 'Pending review', tone: 'warning' },
  VERIFIED: { label: 'Verified', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'brand' },
  ACTION_REQUIRED: { label: 'Action required', tone: 'warning' },
};
