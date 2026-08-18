import { Link } from 'react-router-dom';
import { useDashboardQuery } from '../adminApi';
import PageHeader from '../../../components/layout/PageHeader';
import Card, { StatCard } from '../../../components/ui/Card';
import BarChart from '../../../components/ui/BarChart';

/**
 * Admin overview: platform KPIs, a requests-by-status chart, and a shortcut to
 * pending verifications. All figures are live from /admin/dashboard.
 */
export default function AdminDashboardPage() {
  const { data, isLoading } = useDashboardQuery();
  const s = data?.data;

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

  const pending = s.pendingHospitalVerifications + s.pendingBankVerifications;
  const chart = Object.entries(s.requestsByStatus || {}).map(([label, value]) => ({ label, value }));

  return (
    <div className="space-y-6">
      <PageHeader breadcrumb="Home" title="Admin dashboard" subtitle="Platform health at a glance." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total users" value={s.totalUsers} />
        <StatCard label="Patients" value={s.patients} />
        <StatCard label="Donors" value={s.donors} />
        <StatCard label="Hospitals" value={s.hospitals} />
        <StatCard label="Blood banks" value={s.bloodBanks} />
        <StatCard label="Total requests" value={s.totalRequests} />
        <StatCard label="Completed donations" value={s.completedDonations} accent="text-success" />
        <StatCard label="Units in stock" value={s.totalUnitsInStock} accent="text-ocean-600" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-4 text-lg font-semibold">Requests by status</h2>
          <BarChart data={chart} />
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <h2 className="text-lg font-semibold">Pending verifications</h2>
            <p className="mt-2 text-4xl font-bold text-brand-600">{pending}</p>
            <p className="mt-1 text-sm text-slate-500">
              {s.pendingHospitalVerifications} hospital(s), {s.pendingBankVerifications} bank(s)
            </p>
          </div>
          <Link to="/admin/verifications"
                className="mt-4 inline-flex rounded-xl bg-brand-600 px-4 py-2.5 font-semibold text-white hover:bg-brand-700">
            Review now
          </Link>
        </Card>
      </div>
    </div>
  );
}
