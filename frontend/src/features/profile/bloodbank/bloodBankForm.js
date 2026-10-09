/**
 * Blood bank registration form model: empty state, mapping to/from the API,
 * client-side validation (mirrors BloodBankRequest on the server, which remains
 * the authority) and the profile-completion calculation.
 */

export const LICENSE_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';
export const LICENSE_MAX_BYTES = 5 * 1024 * 1024;
export const LOGO_ACCEPT = '.jpg,.jpeg,.png,image/jpeg,image/png';
export const LOGO_MAX_BYTES = 2 * 1024 * 1024;

export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh',
  'Chhattisgarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep',
  'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Puducherry',
  'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand',
  'West Bengal',
];

export const EMPTY_FORM = {
  bankName: '', contactEmail: '', phone: '', alternatePhone: '', website: '',
  address: '', state: '', city: '', pincode: '', latitude: '', longitude: '',
  licenseNumber: '', licenseIssueDate: '', licenseExpiryDate: '',
};

/** Server profile → form strings (null → ''). */
export function fromProfile(p) {
  if (!p) return { ...EMPTY_FORM };
  const out = {};
  Object.keys(EMPTY_FORM).forEach((k) => { out[k] = p[k] == null ? '' : String(p[k]); });
  return out;
}

/** Form → request body. Optional blanks become null; coordinates become numbers. */
export function toBody(form) {
  const t = (v) => (v ?? '').trim();
  const opt = (v) => (t(v) === '' ? null : t(v));
  return {
    bankName: t(form.bankName),
    contactEmail: t(form.contactEmail),
    phone: t(form.phone),
    alternatePhone: opt(form.alternatePhone),
    website: opt(form.website),
    address: t(form.address),
    state: t(form.state),
    city: t(form.city),
    pincode: t(form.pincode),
    latitude: t(form.latitude) === '' ? null : Number(form.latitude),
    longitude: t(form.longitude) === '' ? null : Number(form.longitude),
    licenseNumber: t(form.licenseNumber),
    licenseIssueDate: opt(form.licenseIssueDate),
    licenseExpiryDate: opt(form.licenseExpiryDate),
  };
}

const PHONE = /^[0-9+\- ]{7,15}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const WEBSITE = /^https?:\/\/\S+\.\S+$/;
const PIN = /^[1-9][0-9]{5}$/;
const LICENSE = /^[A-Za-z0-9/\-. ]+$/;
const NUMBER = /^-?\d+(\.\d+)?$/;

function todayIso() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** Validate one field; returns an error message or ''. */
export function validateField(name, form) {
  const v = (form[name] ?? '').trim();
  switch (name) {
    case 'bankName':
      if (!v) return 'Blood bank name is required';
      return v.length > 100 ? 'Use at most 100 characters' : '';
    case 'contactEmail':
      if (!v) return 'Official contact email is required';
      return EMAIL.test(v) && v.length <= 150 ? '' : 'Enter a valid email address';
    case 'phone':
      if (!v) return 'Official contact phone is required';
      return PHONE.test(v) ? '' : 'Enter a valid phone number (7–15 digits)';
    case 'alternatePhone':
      return !v || PHONE.test(v) ? '' : 'Enter a valid phone number (7–15 digits)';
    case 'website':
      return !v || (WEBSITE.test(v) && v.length <= 255) ? '' : 'Enter a full URL starting with https://';
    case 'address':
      if (!v) return 'Full address is required';
      return v.length > 255 ? 'Use at most 255 characters' : '';
    case 'state':
      return v ? '' : 'State is required';
    case 'city':
      if (!v) return 'City is required';
      return v.length > 50 ? 'Use at most 50 characters' : '';
    case 'pincode':
      if (!v) return 'PIN code is required';
      return PIN.test(v) ? '' : 'PIN code must be 6 digits and cannot start with 0';
    case 'latitude': {
      if (!v) return 'Latitude is required for location-based matching';
      const n = Number(v);
      return NUMBER.test(v) && n >= -90 && n <= 90 ? '' : 'Latitude must be a number between -90 and 90';
    }
    case 'longitude': {
      if (!v) return 'Longitude is required for location-based matching';
      const n = Number(v);
      return NUMBER.test(v) && n >= -180 && n <= 180 ? '' : 'Longitude must be a number between -180 and 180';
    }
    case 'licenseNumber':
      if (!v) return 'License number is required';
      if (v.length > 50) return 'Use at most 50 characters';
      return LICENSE.test(v) ? '' : 'Use letters, digits, / - . and spaces only';
    case 'licenseIssueDate':
      return v && v > todayIso() ? 'Issue date cannot be in the future' : '';
    case 'licenseExpiryDate': {
      const issue = (form.licenseIssueDate ?? '').trim();
      return v && issue && v <= issue ? 'Expiry date must be after the issue date' : '';
    }
    default:
      return '';
  }
}

/** Validate the whole form; returns { field: message } for invalid fields only. */
export function validateForm(form) {
  const errors = {};
  Object.keys(EMPTY_FORM).forEach((k) => {
    const e = validateField(k, form);
    if (e) errors[k] = e;
  });
  return errors;
}

/** Validate a chosen file against an extension allow-list and size cap. */
export function validateFile(file, { exts, maxBytes, label }) {
  if (!file) return `Choose a ${label}`;
  const ext = file.name.split('.').pop()?.toLowerCase();
  if (!exts.includes(ext)) {
    return `${label[0].toUpperCase()}${label.slice(1)} must be ${exts.filter((e) => e !== 'jpeg').map((e) => e.toUpperCase()).join(', ')}`;
  }
  if (file.size === 0) return 'The file is empty';
  if (file.size > maxBytes) return `File is ${formatBytes(file.size)} — the limit is ${formatBytes(maxBytes)}`;
  return '';
}

export function formatBytes(n) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/** Required items that make a profile complete. License dates are "where applicable". */
export const REQUIRED_ITEMS = [
  { key: 'bankName', label: 'Blood bank name', section: 'basic' },
  { key: 'contactEmail', label: 'Official email', section: 'contact' },
  { key: 'phone', label: 'Official phone', section: 'contact' },
  { key: 'address', label: 'Full address', section: 'address' },
  { key: 'state', label: 'State', section: 'address' },
  { key: 'city', label: 'City', section: 'address' },
  { key: 'pincode', label: 'PIN code', section: 'address' },
  { key: 'latitude', label: 'Latitude', section: 'address' },
  { key: 'longitude', label: 'Longitude', section: 'address' },
  { key: 'licenseNumber', label: 'License number', section: 'license' },
  { key: 'licenseDocument', label: 'License certificate', section: 'license' },
];

/**
 * Completion = share of required items that are present AND valid. The license
 * certificate counts when one is saved or a valid new file is staged. This says
 * nothing about verification, which only an administrator can grant.
 */
export function completion(form, hasDocument) {
  const items = REQUIRED_ITEMS.map((it) => ({
    ...it,
    done: it.key === 'licenseDocument'
      ? hasDocument
      : (form[it.key] ?? '').trim() !== '' && !validateField(it.key, form),
  }));
  const done = items.filter((i) => i.done).length;
  return { items, percent: Math.round((done / items.length) * 100) };
}
