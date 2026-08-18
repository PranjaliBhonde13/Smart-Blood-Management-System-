import { useState } from 'react';
import { useSelector } from 'react-redux';
import { useRequestsSummaryQuery, useDonationsSummaryQuery, useInventorySummaryQuery } from '../reportsApi';
import { API_BASE_URL } from '../../../lib/constants';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import BarChart from '../../../components/ui/BarChart';

/** Admin analytics summaries plus a CSV export of recent requests. */
export default function ReportsPage() {
  const token = useSelector((s) => s.auth?.accessToken);
  const { data: reqData } = useRequestsSummaryQuery();
  const { data: donData } = useDonationsSummaryQuery();
  const { data: invData } = useInventorySummaryQuery();
  const [downloading, setDownloading] = useState(false);

  const requests = reqData?.data || {};
  const donations = donData?.data || {};
  const inventory = invData?.data || {};

  // The export is a file stream (not JSON), so fetch it directly with the token
  // and trigger a browser download from the returned blob.
  const downloadCsv = async () => {
    setDownloading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/reports/export?type=requests`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error();
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'requests-report.csv';
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Report downloaded');
    } catch {
      toast.error('Export failed');
    } finally {
      setDownloading(false);
    }
  };

  const reqChart = Object.entries(requests).filter(([k]) => k !== 'TOTAL').map(([label, value]) => ({ label, value }));
  const donChart = Object.entries(donations).map(([label, value]) => ({ label, value }));

  return (
    <div className="space-y-6">
      <PageHeader breadcrumb="Home / Reports" title="Reports & analytics"
                  subtitle="Operational summaries and exports."
                  actions={<Button loading={downloading} onClick={downloadCsv}>⬇ Export requests (CSV)</Button>} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-lg font-semibold">Requests by status</h2>
          <BarChart data={reqChart} />
          <p className="mt-4 text-sm text-slate-500">Total: <b>{requests.TOTAL ?? 0}</b></p>
        </Card>

        <Card>
          <h2 className="mb-4 text-lg font-semibold">Donations by status</h2>
          <BarChart data={donChart} />
        </Card>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Card><div className="text-sm text-slate-500">Total units in stock</div>
          <div className="mt-1 text-3xl font-bold text-ocean-600">{inventory.totalUnits ?? 0}</div></Card>
        <Card><div className="text-sm text-slate-500">Stock lines</div>
          <div className="mt-1 text-3xl font-bold">{inventory.stockLines ?? 0}</div></Card>
      </div>
    </div>
  );
}
