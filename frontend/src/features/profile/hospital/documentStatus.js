import { ShieldCheck, ShieldAlert, ShieldX, Clock, FileQuestion, UserRoundPen, CalendarX } from 'lucide-react';

/**
 * Display metadata for hospital verification. Every status shown here comes
 * from the API; the client never computes or upgrades a verification status.
 */

/** Per-document review status (and per-requirement state, which adds NOT_SUBMITTED). */
export const DOC_STATUS = {
  NOT_SUBMITTED: { label: 'Not submitted', tone: 'neutral', Icon: FileQuestion },
  PENDING: { label: 'Pending verification', tone: 'warning', Icon: Clock },
  VERIFIED: { label: 'Verified', tone: 'success', Icon: ShieldCheck },
  REJECTED: { label: 'Rejected', tone: 'brand', Icon: ShieldX },
  EXPIRED: { label: 'Expired', tone: 'brand', Icon: CalendarX },
};

/** Overall hospital status (HospitalVerificationStatus on the server). */
export const OVERALL_STATUS = {
  PROFILE_INCOMPLETE: {
    label: 'Profile incomplete', tone: 'neutral', Icon: UserRoundPen,
    panel: 'border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/50',
  },
  DOCUMENTS_PENDING: {
    label: 'Documents pending', tone: 'warning', Icon: Clock,
    panel: 'border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30',
  },
  ACTION_REQUIRED: {
    label: 'Action required', tone: 'brand', Icon: ShieldX,
    panel: 'border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30',
  },
  VERIFICATION_EXPIRED: {
    label: 'Verification expired', tone: 'brand', Icon: ShieldAlert,
    panel: 'border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30',
  },
  VERIFIED: {
    label: 'Verified', tone: 'success', Icon: ShieldCheck,
    panel: 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30',
  },
};

export const DOC_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';
export const DOC_EXTS = ['pdf', 'jpg', 'jpeg', 'png'];

export function formatBytes(n) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/** Client-side pre-check only; the server re-validates type, content and size. */
export function validateDocFile(file, maxBytes) {
  if (!file) return 'Choose a file';
  const ext = file.name.split('.').pop()?.toLowerCase();
  if (!DOC_EXTS.includes(ext)) return 'Only PDF, JPG and PNG files are allowed';
  if (file.size === 0) return 'The file is empty';
  if (maxBytes && file.size > maxBytes) return `File exceeds the ${formatBytes(maxBytes)} limit`;
  return '';
}

export const hospitalDocPath = (id, download = false) =>
  `/hospitals/me/documents/${id}/file${download ? '?download=true' : ''}`;
export const adminDocPath = (id, download = false) =>
  `/admin/hospital-documents/${id}/file${download ? '?download=true' : ''}`;
