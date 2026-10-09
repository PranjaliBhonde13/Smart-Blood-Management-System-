import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Megaphone, ArrowRight, CheckCircle2, ShieldCheck, Package } from 'lucide-react';
import { useDashboardQuery, useStuckRequestsQuery, useEscalateRequestMutation, useNetworkInventoryQuery } from '../adminApi';
import { parseApiError } from '../../../lib/apiError';
import { formatMinutes } from '../../../lib/format';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card, { StatCard } from '../../../components/ui/Card';
import BarChart from '../../../components/ui/BarChart';

/**
 * Admin overview: what needs attention now (overdue requests, verifications,
 * shortages) first, then platform KPIs and the request pipeline.
 */
/** Today as YYYY-MM-DD in local time (toISOString would give the UTC date). */
function localIsoDate(now = new Date()) {
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

export default function AdminDashboardPage() {
  const { data, isLoading } = useDashboardQuery(undefined, { pollingInterval: 60000 });
  const { data: stuckData } = useStuckRequestsQuery(undefined, { pollingInterval: 60000 });
  const { data: invData } = useNetworkInventoryQuery();
  const [escalate] = useEscalateRequestMutation();
  const [busyId, setBusyId] = useState(null);
  const s = data?.data;
  const today = localIsoDate();

  if (isLoading || !s) {
    return (
      <div>
        <PageHeader breadcrumb="Home" title="Admin dashboard" />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {[...Array(8)].map((_, i) => <div key={i} className="skeleton h-24" />)}
        </div>
      </div>
    );
  }

  const stuck = stuckData?.data || [];
  const shortages = (invData?.data?.groups || []).filter((g) => g.level === 'low');
  const pending = s.pendingHospitalVerifications + s.pendingBankVerifications;
  const chart = Object.entries(s.requestsByStatus || {}).map(([label, value]) => ({ label, value }));

  const onEscalate = async (r) => {
    setBusyId(r.id);
    try {
      const res = await escalate(r.id).unwrap();
      notify.success(`Escalated #${r.id}: ${res.data.hospitalsAlerted} hospital(s), ${res.data.donorsAlerted} donor(s) alerted`);
    } catch (err) {
      notify.error(parseApiError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader breadcrumb="Home" title="Admin dashboard" subtitle="What needs attention now, then platform health." />

      {/* Needs attention */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <AlertTriangle className={'h-5 w-5 ' + (stuck.length ? 'text-amber-500' : 'text-slate-300')} /> Overdue requests
              <span className={'rounded-full px-2 text-sm ' + (stuck.length ? 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300' : 'bg-slate-100 text-slate-500 dark:bg-slate-800')}>{stuck.length}</span>
            </h2>
            <Link to="/admin/requests" className="inline-flex items-center gap-1 text-sm font-semibold text-brand-600 hover:underline">All requests <ArrowRight className="h-4 w-4" /></Link>
          </div>
          {stuck.length === 0 ? (
            <p className="flex items-center gap-2 py-4 text-sm text-slate-500"><CheckCircle2 className="h-5 w-5 text-success" /> Every waiting request is within its urgency target.</p>
          ) : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {stuck.slice(0, 5).map((r) => (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
                  <div className="flex min-w-0 flex-wrap items-center gap-2 text-sm">
                    <span className="text-slate-400">#{r.id}</span>
                    <b className="text-brand-600">{r.bloodGroup} × {r.unitsRequired}</b>
                    <UrgencyBadge urgency={r.urgency} />
                    <StatusBadge status={r.status} />
                    <span className="text-slate-500">waiting {formatMinutes(r.minutesInStatus)} (target {formatMinutes(r.slaMinutes)})</span>
                    <span className="text-slate-400">· {r.hospitalName || 'no hospital yet'}</span>
                  </div>
                  <button type="button" onClick={() => onEscalate(r)} disabled={busyId !== null}
                          className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-200 hover:bg-amber-50 disabled:opacity-50 dark:text-amber-300 dark:ring-amber-900 dark:hover:bg-amber-900/20">
                    <Megaphone className="h-3.5 w-3.5" /> {busyId === r.id ? 'Escalating…' : 'Escalate'}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {stuck.length > 5 && <Link to="/admin/requests" className="mt-2 inline-block text-sm text-slate-500 hover:underline">+{stuck.length - 5} more</Link>}
        </Card>

        <div className="space-y-4">
          <Card>
            <h2 className="flex items-center gap-2 text-base font-semibold"><ShieldCheck className="h-5 w-5 text-ocean-600" /> Pending verifications</h2>
            <p className="mt-1 text-3xl font-bold text-brand-600">{pending}</p>
            <p className="text-sm text-slate-500">{s.pendingHospitalVerifications} hospital(s), {s.pendingBankVerifications} bank(s)</p>
            <Link to="/admin/verifications" className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-brand-600 hover:underline">Review now <ArrowRight className="h-4 w-4" /></Link>
          </Card>
          <Card>
            <h2 className="flex items-center gap-2 text-base font-semibold"><Package className="h-5 w-5 text-brand-600" /> Stock shortages</h2>
            {shortages.length === 0
              ? <p className="mt-1 text-sm text-slate-500">All blood groups above the shortage level.</p>
              : <p className="mt-1 text-sm font-medium text-brand-700 dark:text-red-300">Low network stock: {shortages.map((g) => `${g.bloodGroup} (${g.units})`).join(', ')}</p>}
            <div className="mt-3 flex flex-wrap gap-3 text-sm font-semibold">
              <Link to="/admin/inventory" className="text-brand-600 hover:underline">Inventory</Link>
              {shortages.length > 0 && <Link to="/admin/broadcast" className="text-brand-600 hover:underline">Appeal to donors</Link>}
            </div>
          </Card>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total users" value={s.totalUsers} to="/admin/users" />
        <StatCard label="Patients" value={s.patients} to="/admin/users?role=PATIENT" />
        <StatCard label="Donors" value={s.donors} to="/admin/users?role=DONOR" />
        <StatCard label="Hospitals" value={s.hospitals} to="/admin/users?role=HOSPITAL" />
        <StatCard label="Blood banks" value={s.bloodBanks} to="/admin/users?role=BLOOD_BANK" />
        <StatCard label="Requests today" value={s.requestsToday} to={`/admin/requests?from=${today}&to=${today}`} />
        <StatCard label="Completed donations" value={s.completedDonations} accent="text-success" to="/admin/requests?status=COMPLETED" />
        <StatCard label="Units in stock" value={s.totalUnitsInStock} accent="text-ocean-600" to="/admin/inventory" />
      </div>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Requests by status (all time: {s.totalRequests})</h2>
          <Link to="/admin/analytics" className="inline-flex items-center gap-1 text-sm font-semibold text-brand-600 hover:underline">Analytics <ArrowRight className="h-4 w-4" /></Link>
        </div>
        <BarChart data={chart} />
      </Card>
    </div>
  );
}
