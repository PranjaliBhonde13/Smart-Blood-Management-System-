import { CheckCircle2, Circle, AlertCircle } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { OVERALL_STATUS } from './documentStatus';

/**
 * Overall hospital status card at the top of the profile. Shows the
 * server-computed status and message, plus the three separate steps:
 * profile completion, document verification and administrator approval.
 */
export default function HospitalVerificationOverview({ profile }) {
  const status = profile.verificationStatus || (profile.verified ? 'VERIFIED' : 'DOCUMENTS_PENDING');
  const meta = OVERALL_STATUS[status] || OVERALL_STATUS.DOCUMENTS_PENDING;
  const { Icon } = meta;

  const steps = [
    {
      label: 'Profile details',
      done: status !== 'PROFILE_INCOMPLETE',
      note: status === 'PROFILE_INCOMPLETE' ? 'Missing details' : 'Complete',
    },
    {
      label: 'Documents',
      done: !!profile.documentsComplete,
      problem: status === 'ACTION_REQUIRED' || status === 'VERIFICATION_EXPIRED',
      note: profile.documentsComplete ? 'All mandatory documents approved'
        : profile.pendingDocuments ? `${profile.pendingDocuments} awaiting review` : 'Not complete',
    },
    {
      label: 'Administrator approval',
      done: !!profile.verified,
      problem: !!profile.rejectionReason,
      note: profile.verified ? 'Approved' : profile.rejectionReason ? 'Rejected' : 'Pending',
    },
  ];

  return (
    <section aria-labelledby="hospital-status-heading" role="status"
             className={`mb-6 rounded-2xl border p-5 text-sm text-slate-700 dark:text-slate-200 ${meta.panel}`}>
      <div className="flex flex-wrap items-center gap-3">
        <Icon className="h-6 w-6 shrink-0" aria-hidden="true" />
        <h2 id="hospital-status-heading" className="text-base font-bold text-slate-900 dark:text-white">Verification status</h2>
        <span className="ml-auto"><Badge tone={meta.tone} dot>{meta.label}</Badge></span>
      </div>
      {profile.verificationMessage && <p className="mt-2">{profile.verificationMessage}</p>}
      {!profile.fullyVerified && (
        <p className="mt-1 text-xs opacity-80">
          Until your hospital is fully verified you can view requests, but you cannot verify, approve or complete them.
        </p>
      )}

      <ol className="mt-4 grid gap-2 sm:grid-cols-3">
        {steps.map((s) => {
          const StepIcon = s.done ? CheckCircle2 : s.problem ? AlertCircle : Circle;
          return (
            <li key={s.label} className="flex items-start gap-2 rounded-xl bg-white/70 px-3 py-2.5 dark:bg-slate-900/50">
              <StepIcon aria-hidden="true"
                        className={`mt-0.5 h-4 w-4 shrink-0 ${s.done ? 'text-success' : s.problem ? 'text-brand-600' : 'text-slate-400'}`} />
              <div className="min-w-0">
                <p className="font-semibold text-slate-800 dark:text-slate-100">{s.label}</p>
                <p className="text-xs text-slate-500">{s.note}</p>
              </div>
            </li>
          );
        })}
      </ol>

      <a href="#hospital-documents" className="mt-3 inline-block text-xs font-semibold text-ocean-600 hover:underline">
        Go to Documents &amp; Verification ↓
      </a>
    </section>
  );
}
