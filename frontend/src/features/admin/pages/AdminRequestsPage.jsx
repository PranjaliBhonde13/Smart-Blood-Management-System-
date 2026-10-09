import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Siren, Megaphone, FileText, X, Ban, AlertTriangle } from 'lucide-react';
import { useAdminRequestsQuery, useStuckRequestsQuery, useEscalateRequestMutation } from '../adminApi';
import { useUpdateRequestStatusMutation } from '../../requests/requestsApi';
import { parseApiError } from '../../../lib/apiError';
import { openReport } from '../../../lib/openReport';
import { formatMinutes, formatDateTime } from '../../../lib/format';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge, Badge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import Pager from '../../../components/ui/Pager';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';

const STATUSES = ['', 'PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED', 'COMPLETED', 'REJECTED', 'CANCELLED'];
const URGENCIES = ['', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const GROUPS = ['', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
const WAITING = ['PENDING', 'VERIFIED', 'APPROVED'];
const REJECTABLE = ['PENDING', 'VERIFIED', 'APPROVED'];
const CANCELLABLE = ['PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED'];

const opt = (v, all) => ({ value: v, label: v ? v.charAt(0) + v.slice(1).toLowerCase() : all });

/**
 * Admin request monitor: every request on the network with filters, the
 * waiting time in its current status against the urgency SLA, and admin
 * actions — escalate (re-alert hospital/donors), reject or cancel.
 */
export default function AdminRequestsPage() {
  const [tab, setTab] = useState('all');
  // ?status=, ?from=, ?to= (e.g. from a dashboard tile) preset the filters; invalid values are ignored.
  const [searchParams] = useSearchParams();
  const [filters, setFilters] = useState(() => {
    const param = (k, ok) => { const v = searchParams.get(k) || ''; return ok(v) ? v : ''; };
    const isDate = (v) => /^\d{4}-\d{2}-\d{2}$/.test(v);
    return {
      status: param('status', (v) => STATUSES.includes(v)),
      urgency: '',
      bloodGroup: '',
      from: param('from', isDate),
      to: param('to', isDate),
    };
  });
  const [page, setPage] = useState(0);
  const token = useSelector((s) => s.auth?.accessToken);
  const all = useAdminRequestsQuery({ ...filters, page }, { skip: tab !== 'all' });
  const stuck = useStuckRequestsQuery(undefined, { pollingInterval: 60000 });
  const [escalate] = useEscalateRequestMutation();
  const [updateStatus] = useUpdateRequestStatusMutation();
  const [busyId, setBusyId] = useState(null);

  const rows = tab === 'all' ? all.data?.data?.content || [] : stuck.data?.data || [];
  const loading = tab === 'all' ? all.isLoading : stuck.isLoading;
  const stuckCount = stuck.data?.data?.length || 0;

  const setFilter = (k) => (e) => { setFilters((f) => ({ ...f, [k]: e.target.value })); setPage(0); };

  const run = async (id, fn, ok) => {
    setBusyId(id);
    try {
      const res = await fn().unwrap();
      notify.success(typeof ok === 'function' ? ok(res) : ok);
    } catch (err) {
      notify.error(parseApiError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  const onEscalate = (r) => run(r.id, () => escalate(r.id),
    (res) => `Escalated #${r.id}: ${res.data.hospitalsAlerted} hospital(s), ${res.data.donorsAlerted} donor(s) alerted`);
  const onStatus = (r, status, verb) => {
    if (!window.confirm(`${verb} request #${r.id}? The patient will be notified.`)) return;
    run(r.id, () => updateStatus({ id: r.id, status }), `Request #${r.id} ${status.toLowerCase()}`);
  };

  return (
    <div>
      <PageHeader icon={Siren} breadcrumb="Home / Requests" title="Request monitor"
                  subtitle="Every blood request on the network, with waiting time against its urgency target." />

      <div className="mb-4 flex flex-wrap gap-2">
        <button type="button" onClick={() => setTab('all')}
                className={'rounded-full px-4 py-1.5 text-sm font-semibold ring-1 ring-inset ' + (tab === 'all' ? 'bg-brand-600 text-white ring-transparent' : 'bg-white text-slate-600 ring-slate-200 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700')}>
          All requests
        </button>
        <button type="button" onClick={() => setTab('stuck')}
                className={'inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-semibold ring-1 ring-inset ' + (tab === 'stuck' ? 'bg-brand-600 text-white ring-transparent' : 'bg-white text-slate-600 ring-slate-200 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700')}>
          <AlertTriangle className="h-4 w-4" /> Overdue
          <span className={'rounded-full px-1.5 text-xs ' + (stuckCount ? 'bg-amber-400 text-slate-900' : 'bg-slate-200 text-slate-500 dark:bg-slate-700')}>{stuckCount}</span>
        </button>
      </div>

      {tab === 'all' && (
        <Card className="mb-4">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            <Select label="Status" value={filters.status} onChange={setFilter('status')} options={STATUSES.map((s) => opt(s, 'All statuses'))} />
            <Select label="Urgency" value={filters.urgency} onChange={setFilter('urgency')} options={URGENCIES.map((s) => opt(s, 'Any urgency'))} />
            <Select label="Blood group" value={filters.bloodGroup} onChange={setFilter('bloodGroup')} options={GROUPS.map((g) => ({ value: g, label: g || 'Any group' }))} />
            <TextField label="From" type="date" value={filters.from} onChange={setFilter('from')} />
            <TextField label="To" type="date" value={filters.to} onChange={setFilter('to')} />
          </div>
        </Card>
      )}

      {tab === 'stuck' && (
        <p className="mb-4 text-sm text-slate-500">
          Waiting longer than the target for their urgency: Critical 30m · High 2h · Medium 12h · Low 48h. Refreshes every minute.
        </p>
      )}

      {loading ? (
        <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
          {[...Array(5)].map((_, i) => <div key={i} className="flex gap-4 p-4"><Skeleton className="h-6 w-12" /><Skeleton className="h-6 flex-1" /></div>)}
        </Card>
      ) : rows.length === 0 ? (
        <EmptyState icon={Siren} title={tab === 'stuck' ? 'Nothing overdue' : 'No requests match'}
                    message={tab === 'stuck' ? 'Every waiting request is within its urgency target.' : 'Try widening the filters.'} />
      ) : (
        <>
          <Table>
            <THead>
              <TH>#</TH><TH>Patient</TH><TH>Need</TH><TH>Urgency</TH><TH>Status</TH><TH>Hospital</TH><TH>In status</TH><TH>Raised</TH><TH className="text-right">Actions</TH>
            </THead>
            <TBody>
              {rows.map((r) => (
                <TR key={r.id}>
                  <TD className="text-slate-400">#{r.id}</TD>
                  <TD>
                    {r.patientUserId
                      ? <Link to={`/admin/users/${r.patientUserId}`} className="font-medium text-slate-800 hover:text-brand-600 hover:underline dark:text-slate-100">{r.patientName}</Link>
                      : <span className="text-slate-400">—</span>}
                    {r.patientPhone && <div className="text-xs text-slate-400">{r.patientPhone}</div>}
                  </TD>
                  <TD>
                    <span className="font-bold text-brand-600">{r.bloodGroup}</span>
                    <span className="text-slate-500"> × {r.unitsRequired}</span>
                    {r.hasReport && (
                      <button type="button" onClick={() => openReport(r.id, token)} title="View medical report"
                              className="ml-2 inline-flex align-middle text-ocean-600 hover:text-ocean-700"><FileText className="h-4 w-4" /></button>
                    )}
                  </TD>
                  <TD><UrgencyBadge urgency={r.urgency} /></TD>
                  <TD><StatusBadge status={r.status} /></TD>
                  <TD className="text-slate-600 dark:text-slate-300">{r.hospitalName || <span className="text-slate-400">Unassigned</span>}</TD>
                  <TD className="whitespace-nowrap">
                    {WAITING.includes(r.status)
                      ? r.overdue
                        ? <Badge tone="warning" dot>{formatMinutes(r.minutesInStatus)} · over {formatMinutes(r.slaMinutes)}</Badge>
                        : <span className="text-slate-500">{formatMinutes(r.minutesInStatus)}</span>
                      : <span className="text-slate-400">—</span>}
                  </TD>
                  <TD className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(r.requestDate)}</TD>
                  <TD>
                    <div className="flex items-center justify-end gap-1">
                      {WAITING.includes(r.status) && (
                        <button type="button" disabled={busyId !== null} onClick={() => onEscalate(r)} title="Re-alert hospital and donors"
                                className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-amber-700 hover:bg-amber-50 disabled:opacity-50 dark:text-amber-300 dark:hover:bg-amber-900/20">
                          <Megaphone className="h-3.5 w-3.5" /> Escalate
                        </button>
                      )}
                      {REJECTABLE.includes(r.status) && (
                        <button type="button" disabled={busyId !== null} onClick={() => onStatus(r, 'REJECTED', 'Reject')}
                                className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-brand-600 hover:bg-brand-50 disabled:opacity-50 dark:hover:bg-brand-900/20">
                          <X className="h-3.5 w-3.5" /> Reject
                        </button>
                      )}
                      {CANCELLABLE.includes(r.status) && (
                        <button type="button" disabled={busyId !== null} onClick={() => onStatus(r, 'CANCELLED', 'Cancel')}
                                className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-slate-500 hover:bg-slate-100 disabled:opacity-50 dark:hover:bg-slate-800">
                          <Ban className="h-3.5 w-3.5" /> Cancel
                        </button>
                      )}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
          {tab === 'all' && <Pager page={page} totalPages={all.data?.data?.totalPages} onChange={setPage} />}
        </>
      )}
    </div>
  );
}
