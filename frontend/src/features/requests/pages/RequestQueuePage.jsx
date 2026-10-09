import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import { ClipboardList, CheckCircle2, ShieldCheck, X, FileText } from 'lucide-react';
import { useRequestQueueQuery, useVerifyRequestMutation, useUpdateRequestStatusMutation } from '../requestsApi';
import ConfirmBloodGroupModal, { BloodGroupSourceBadge, RecipientCell } from './ConfirmBloodGroupModal';
import { useCompleteDonationMutation } from '../../donations/donationsApi';
import { useGetMyProfileQuery } from '../../profile/profileApi';
import { parseApiError } from '../../../lib/apiError';
import { openReport } from '../../../lib/openReport';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import Pager from '../../../components/ui/Pager';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';
import { formatDateTime } from '../../../lib/format';

const STATUS_FILTERS = ['', 'PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED', 'COMPLETED', 'REJECTED', 'CANCELLED'];

/**
 * Hospital work queue. Filter by status and drive requests through the
 * lifecycle: verify PENDING, then approve → fulfil → complete. Buttons shown
 * per row match exactly the transitions the backend allows; requests already
 * owned by another hospital are read-only here.
 */
export default function RequestQueuePage() {
  const [status, setStatus] = useState('PENDING');
  const [page, setPage] = useState(0);
  const token = useSelector((s) => s.auth?.accessToken);
  const { data, isLoading } = useRequestQueueQuery({ status, page });
  const { data: profile } = useGetMyProfileQuery('hospitals');
  const [verifyRequest] = useVerifyRequestMutation();
  const [updateStatus] = useUpdateRequestStatusMutation();
  const [completeDonation] = useCompleteDonationMutation();
  const [busyId, setBusyId] = useState(null);
  const [confirming, setConfirming] = useState(null); // request whose blood group is being confirmed/corrected

  const rows = data?.data?.content || [];
  const myHospital = profile?.data;
  // fullyVerified = admin approval + all mandatory documents approved and unexpired (enforced by the API too).
  const unverified = myHospital && !(myHospital.fullyVerified ?? myHospital.verified);

  const run = async (id, action, successMsg) => {
    setBusyId(id);
    try {
      await action().unwrap();
      notify.success(successMsg);
    } catch (err) {
      notify.error(parseApiError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  const nextAction = (r) => {
    switch (r.status) {
      case 'PENDING':  return { label: 'Verify', run: () => run(r.id, () => verifyRequest(r.id), `Request #${r.id} verified`) };
      case 'VERIFIED': if (r.bloodGroupApprovable === false) return { label: 'Confirm group', run: () => setConfirming(r) };
        return { label: 'Approve', run: () => run(r.id, () => updateStatus({ id: r.id, status: 'APPROVED' }), `Request #${r.id} approved`) };
      case 'APPROVED': return { label: 'Awaiting donor', disabled: true };
      case 'FULFILLED':return { label: 'Complete', run: () => run(r.id, () => completeDonation(r.id), `Request #${r.id} completed`) };
      default: return null;
    }
  };
  const canReject = (s) => ['PENDING', 'VERIFIED', 'APPROVED'].includes(s);
  const onReject = (id) => {
    if (!window.confirm(`Reject request #${id}? The patient will be notified.`)) return;
    run(id, () => updateStatus({ id, status: 'REJECTED' }), `Request #${id} rejected`);
  };
  const ownedByOther = (r) => myHospital && r.hospitalId != null && r.hospitalId !== myHospital.id;

  const pickStatus = (s) => { setStatus(s); setPage(0); };

  return (
    <div>
      <PageHeader icon={ClipboardList} breadcrumb="Home / Request Queue" title="Emergency request queue"
                  subtitle="Verify and progress patient blood requests." />

      {unverified && (
        <div className="mb-5">
          <Alert type="error" title="Your hospital is not fully verified">
            {myHospital.verificationMessage || 'Your hospital is awaiting administrator verification.'}{' '}
            You can view the queue, but verifying and progressing requests is enabled once verification is complete.{' '}
            <Link to="/hospital/profile" className="font-semibold underline">Go to Documents &amp; Verification</Link>
          </Alert>
        </div>
      )}

      <div className="mb-5 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((s) => (
          <button key={s || 'ALL'} onClick={() => pickStatus(s)}
                  className={'rounded-full px-3.5 py-1.5 text-sm font-semibold ring-1 ring-inset transition ' +
                    (status === s
                      ? 'bg-grad-brand text-white ring-transparent shadow-emergency'
                      : 'bg-white text-slate-600 ring-slate-200 hover:bg-slate-50 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700')}>
            {s ? s.charAt(0) + s.slice(1).toLowerCase() : 'All'}
          </button>
        ))}
      </div>

      {isLoading && (
        <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4">
              <Skeleton className="h-8 w-10" /><Skeleton className="h-4 flex-1" /><Skeleton className="h-8 w-24 rounded-xl" />
            </div>
          ))}
        </Card>
      )}

      {!isLoading && rows.length === 0 && (
        <EmptyState icon={CheckCircle2} title="Queue is clear" message="No requests match this filter." />
      )}

      {rows.length > 0 && (
        <>
          <Table>
            <THead>
              <TH>#</TH><TH>Patient</TH><TH>Group</TH><TH>Units</TH><TH>Urgency</TH><TH>Status</TH><TH>Report</TH><TH>Raised</TH><TH className="text-right">Action</TH>
            </THead>
            <TBody>
              {rows.map((r) => {
                const action = ownedByOther(r) ? null : nextAction(r);
                return (
                  <TR key={r.id}>
                    <TD className="text-slate-400">#{r.id}</TD>
                    <TD><RecipientCell r={r} /></TD>
                    <TD>
                      <span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{r.bloodGroup}</span>
                      <BloodGroupSourceBadge r={r}
                        onConfirm={!ownedByOther(r) && !unverified && ['PENDING', 'VERIFIED'].includes(r.status) ? () => setConfirming(r) : null} />
                    </TD>
                    <TD className="font-semibold">{r.unitsRequired}</TD>
                    <TD><UrgencyBadge urgency={r.urgency} /></TD>
                    <TD><StatusBadge status={r.status} /></TD>
                    <TD>
                      {r.medicalReport
                        ? <button type="button" onClick={() => openReport(r.id, token)} className="inline-flex items-center gap-1 text-sm font-medium text-ocean-600 hover:underline"><FileText className="h-4 w-4" /> View</button>
                        : <span className="text-xs text-slate-400">None</span>}
                    </TD>
                    <TD className="whitespace-nowrap text-slate-500">{formatDateTime(r.requestDate)}</TD>
                    <TD>
                      <div className="flex items-center justify-end gap-2">
                        {ownedByOther(r) ? (
                          <span className="text-xs text-slate-400">Another hospital</span>
                        ) : action ? (
                          action.disabled
                            ? <span className="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-400 dark:bg-slate-800">{action.label}</span>
                            : <Button size="sm" className="!px-3 !py-1.5 text-xs" loading={busyId === r.id} disabled={unverified || (busyId !== null && busyId !== r.id)} onClick={action.run} leftIcon={<ShieldCheck className="h-3.5 w-3.5" />}>{action.label}</Button>
                        ) : <span className="text-xs text-slate-400">—</span>}
                        {!ownedByOther(r) && !unverified && canReject(r.status) && (
                          <button onClick={() => onReject(r.id)} disabled={busyId !== null} className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-xs font-medium text-brand-600 transition hover:bg-brand-50 disabled:opacity-60 dark:hover:bg-brand-900/20">
                            <X className="h-3.5 w-3.5" /> Reject
                          </button>
                        )}
                      </div>
                    </TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
          <Pager page={page} totalPages={data?.data?.totalPages} onChange={setPage} />
          <ConfirmBloodGroupModal request={confirming} onClose={() => setConfirming(null)} />
        </>
      )}
    </div>
  );
}
