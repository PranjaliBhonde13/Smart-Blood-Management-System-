/** App-wide constants. API base is env-overridable for prod builds. */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

/** Roles mirror the backend `users.role` enum (docs/03-DATABASE.md). */
export const ROLES = {
  PATIENT: 'PATIENT',
  DONOR: 'DONOR',
  HOSPITAL: 'HOSPITAL',
  BLOOD_BANK: 'BLOOD_BANK',
  ADMIN: 'ADMIN',
};

/** Landing route per role after login. */
export const ROLE_HOME = {
  PATIENT: '/patient',
  DONOR: '/donor',
  HOSPITAL: '/hospital',
  BLOOD_BANK: '/blood-bank',
  ADMIN: '/admin',
};

/** Maps a role to its REST profile resource path (docs/04-API.md §3). */
export const ROLE_RESOURCE = {
  PATIENT: 'patients',
  DONOR: 'donors',
  HOSPITAL: 'hospitals',
  BLOOD_BANK: 'blood-banks',
};

/** Human-readable role labels for the UI. */
export const ROLE_LABEL = {
  PATIENT: 'Patient',
  DONOR: 'Donor',
  HOSPITAL: 'Hospital',
  BLOOD_BANK: 'Blood Bank',
  ADMIN: 'Administrator',
};
