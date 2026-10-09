import { ShieldCheck, ShieldAlert, ShieldX, Clock, FileQuestion } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { formatDate, formatDateTime, formatDay } from '../../../lib/format';

/**
 * Display metadata for each server-derived license status. The status itself
 * always comes from the API (BloodBankResponse.verificationStatus) — the client
 * never computes or upgrades it.
 */
const STATUS_META = {
  NOT_SUBMITTED: { label: 'Not submitted', tone: 'neutral', Icon: FileQuestion, panel: 'border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/50' },
  PENDING: { label: 'Pending verification', tone: 'warning', Icon: Clock, panel: 'border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30' },
  VERIFIED: { label: 'Verified', tone: 'success', Icon: ShieldCheck, panel: 'border-emerald-200 bg-emerald-50 dark:border-emerald-900/60 dark:bg-emerald-950/30' },
  REJECTED: { label: 'Rejected', tone: 'brand', Icon: ShieldX, panel: 'border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30' },
  EXPIRED: { label: 'License expired', tone: 'brand', Icon: ShieldAlert, panel: 'border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30' },
};

export function VerificationBadge({ status }) {
  const meta = STATUS_META[status] || STATUS_META.NOT_SUBMITTED;
  return <Badge tone={meta.tone} dot>{meta.label}</Badge>;
}


function daysUntil(isoDate) {
  if (!isoDate) return null;
  const end = new Date(`${isoDate}T23:59:59`);
  return Math.ceil((end - Date.now()) / 86_400_000);
}

/** Explanatory panel: what the status means and what the bank must do next. */
export function VerificationPanel({ profile }) {
  const status = profile?.verificationStatus || 'NOT_SUBMITTED';
  const meta = STATUS_META[status];
  const { Icon } = meta;
  const left = daysUntil(profile?.licenseExpiryDate);

  let body;
  switch (status) {
    case 'PENDING':
      body = (
        <>
          <p>Your license is awaiting review by a platform administrator. Until it is verified you will not appear
            in patient searches and cannot fulfil blood requests.</p>
          {profile.licenseSubmittedAt && <p className="mt-1 text-xs opacity-80">Submitted {formatDateTime(profile.licenseSubmittedAt)}</p>}
        </>
      );
      break;
    case 'VERIFIED':
      body = (
        <>
          <p>An administrator verified your license{profile.verifiedAt ? ` on ${formatDate(profile.verifiedAt)}` : ''}.
            You are active on the network.</p>
          {profile.licenseExpiryDate && (
            <p className="mt-1 text-xs opacity-80">Valid until {formatDay(profile.licenseExpiryDate)}</p>
          )}
        </>
      );
      break;
    case 'REJECTED':
      body = (
        <>
          <p className="font-medium">Administrator remarks:</p>
          <p className="mt-0.5 whitespace-pre-line rounded-lg bg-white/70 px-3 py-2 dark:bg-slate-900/50">
            {profile.rejectionReason || 'No remarks were provided.'}
          </p>
          <p className="mt-2">Correct the details or upload a new certificate, then save to resubmit for review.</p>
          {profile.rejectedAt && <p className="mt-1 text-xs opacity-80">Reviewed {formatDateTime(profile.rejectedAt)}</p>}
        </>
      );
      break;
    case 'EXPIRED':
      body = (
        <p>Your license expired on <b>{formatDay(profile.licenseExpiryDate)}</b> and is no longer treated as valid.
          You are hidden from searches and cannot fulfil requests. Upload the renewed certificate and update the dates
          to resubmit for verification.</p>
      );
      break;
    default:
      body = <p>Upload your license certificate and save to submit it for administrator verification.</p>;
  }

  return (
    <div className={`rounded-2xl border p-4 text-sm text-slate-700 dark:text-slate-200 ${meta.panel}`} role="status">
      <div className="mb-2 flex items-center gap-2">
        <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />
        <span className="font-semibold">License verification</span>
        <span className="ml-auto"><VerificationBadge status={status} /></span>
      </div>
      {body}
      {status === 'VERIFIED' && left != null && left <= 30 && (
        <p className="mt-2 rounded-lg bg-amber-100 px-3 py-2 text-xs font-medium text-amber-900 dark:bg-amber-900/40 dark:text-amber-100">
          Your license expires in {left} day{left === 1 ? '' : 's'}. Upload the renewed certificate before then to avoid interruption.
        </p>
      )}
    </div>
  );
}
