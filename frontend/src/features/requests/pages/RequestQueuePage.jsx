import { useState } from 'react';
import { ClipboardList, CheckCircle2, ShieldCheck, X } from 'lucide-react';
import { useRequestQueueQuery, useVerifyRequestMutation, useUpdateRequestStatusMutation } from '../requestsApi';
import { useCompleteDonationMutation } from '../../donations/donationsApi';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';

const STATUS_FILTERS = ['', 'PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED', 'COMPLETED', 'REJECTED', 'CANCELLED'];

/**
 * Hospital/Admin work queue. Filter by status and drive requests through the
 * lifecycle: verify PENDING, then approve → fulfil → complete. Buttons shown
 * per row match exactly the transitions the backend allows.
 */
export default function RequestQueuePage() {
  const [status, setStatus] = useState('PENDING');
  const { data, isLoading } = useRequestQueueQuery({ status });
  const [verifyRequest, { isLoading: verifying }] = useVerifyRequestMutation();
  const [updateStatus, { isLoading: updating }] = useUpdateRequestStatusMutation();
  const [completeDonation, { isLoading: completing }] = useCompleteDonationMutation();

  const rows = data?.data?.content || [];
  const busy = verifying || updating || completing;

  const nextAction = (r) => {
    switch (r.status) {
      case 'PENDING':  return { label: 'Verify', run: () => verifyRequest(r.id).unwrap().then(() => notify.success(`Request #${r.id} verified`)) };
      case 'VERIFIED': return { label: 'Approve', run: () => updateStatus({ id: r.id, status: 'APPROVED' }).unwrap().then(() => notify.success(`Request #${r.id} approved`)) };
      case 'APPROVED': return { label: 'Awaiting donor', disabled: true };
      case 'FULFILLED':return { label: 'Complete', run: () => completeDonation(r.id).unwrap().then(() => notify.success(`Request #${r.id} completed`)) };
      default: return null;
    }
  };
  const canReject = (s) => ['PENDING', 'VERIFIED', 'APPROVED'].includes(s);
  const onReject = (id) => updateStatus({ id, status: 'REJECTED' }).unwrap().then(() => notify.info(`Request #${id} rejected`));

  return (
    <div>
      <PageHeader icon={ClipboardList} breadcrumb="Home / Request Queue" title="Emergency request queue"
                  subtitle="Verify and progress patient blood requests." />

      <div className="mb-5 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((s) => (
          <button key={s || 'ALL'} onClick={() => setStatus(s)}
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
        <Table>
          <THead>
            <TH>#</TH><TH>Group</TH><TH>Units</TH><TH>Urgency</TH><TH>Status</TH><TH>Raised</TH><TH className="text-right">Action</TH>
          </THead>
          <TBody>
            {rows.map((r) => {
              const action = nextAction(r);
              return (
                <TR key={r.id}>
                  <TD className="text-slate-400">#{r.id}</TD>
                  <TD><span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{r.bloodGroup}</span></TD>
                  <TD className="font-semibold">{r.unitsRequired}</TD>
                  <TD><UrgencyBadge urgency={r.urgency} /></TD>
                  <TD><StatusBadge status={r.status} /></TD>
                  <TD className="whitespace-nowrap text-slate-500">{new Date(r.requestDate).toLocaleString()}</TD>
                  <TD>
                    <div className="flex items-center justify-end gap-2">
                      {action
                        ? action.disabled
                          ? <span className="inline-flex items-center gap-1 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-400 dark:bg-slate-800">{action.label}</span>
                          : <Button size="sm" className="!px-3 !py-1.5 text-xs" loading={busy} onClick={action.run} leftIcon={<ShieldCheck className="h-3.5 w-3.5" />}>{action.label}</Button>
                        : <span className="text-xs text-slate-400">—</span>}
                      {canReject(r.status) && (
                        <button onClick={() => onReject(r.id)} className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-xs font-medium text-brand-600 transition hover:bg-brand-50 dark:hover:bg-brand-900/20">
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
      )}
    </div>
  );
}
