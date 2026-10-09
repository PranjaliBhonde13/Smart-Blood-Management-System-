import { Heart } from 'lucide-react';
import { useMyDonationsQuery } from '../donationsApi';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';
import { formatDate } from '../../../lib/format';

const DONATION_TONE = { SCHEDULED: 'warning', COMPLETED: 'success', CANCELLED: 'neutral' };

/** Donor's donation history — every request they accepted and its outcome. */
export default function DonationHistoryPage() {
  const { data, isLoading } = useMyDonationsQuery();
  const rows = data?.data || [];
  const completed = rows.filter((d) => d.status === 'COMPLETED').length;

  return (
    <div>
      <PageHeader icon={Heart} breadcrumb="Home / Donation History" title="My donations"
                  subtitle="A record of every life you've helped save." />

      {!isLoading && rows.length > 0 && (
        <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Card className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-900/30"><Heart className="h-5 w-5" /></span>
            <div><div className="text-2xl font-bold text-slate-900 dark:text-white">{rows.length}</div><div className="text-xs text-slate-500">Total pledges</div></div>
          </Card>
          <Card className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-50 text-success dark:bg-emerald-900/30"><Heart className="h-5 w-5 fill-current" /></span>
            <div><div className="text-2xl font-bold text-slate-900 dark:text-white">{completed}</div><div className="text-xs text-slate-500">Completed</div></div>
          </Card>
        </div>
      )}

      {isLoading && (
        <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4"><Skeleton className="h-4 w-16" /><Skeleton className="h-4 flex-1" /><Skeleton className="h-6 w-20 rounded-full" /></div>
          ))}
        </Card>
      )}

      {!isLoading && rows.length === 0 && (
        <EmptyState icon={Heart} title="No donations yet"
                    message="Accept a request from “Open Requests” and it will show up here." />
      )}

      {rows.length > 0 && (
        <Table>
          <THead><TH>Request #</TH><TH>Units</TH><TH>Status</TH><TH>Donation date</TH><TH>Scheduled</TH></THead>
          <TBody>
            {rows.map((d) => (
              <TR key={d.id}>
                <TD className="font-semibold">#{d.requestId}</TD>
                <TD>{d.unitsDonated}</TD>
                <TD><Badge tone={DONATION_TONE[d.status] || 'neutral'} dot>{d.status}</Badge></TD>
                <TD className="text-slate-500">{formatDate(d.donationDate)}</TD>
                <TD className="text-slate-500">{formatDate(d.createdAt)}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
    </div>
  );
}
