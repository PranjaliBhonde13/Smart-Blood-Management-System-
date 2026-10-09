import { useState } from 'react';
import { ClipboardCheck, PackageCheck, Inbox } from 'lucide-react';
import { useRequestQueueQuery } from '../../requests/requestsApi';
import { useFulfillFromBankMutation } from '../donationsApi';
import { UrgencyBadge } from '../../../components/ui/Badge';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';
import { formatDate } from '../../../lib/format';

/**
 * Blood bank view of approved requests it can fulfil directly from stock.
 * Fulfilling decrements the matching group's units and closes the request.
 */
export default function BankRequestsPage() {
  const { data, isLoading } = useRequestQueueQuery({ status: 'APPROVED' });
  const [fulfill, { isLoading: fulfilling }] = useFulfillFromBankMutation();
  const [err, setErr] = useState('');

  const rows = data?.data?.content || [];

  const onFulfill = async (id) => {
    setErr('');
    try {
      await fulfill(id).unwrap();
      notify.success(`Request #${id} fulfilled from stock`);
    } catch (e) {
      const msg = parseApiError(e).message;
      setErr(msg);
      notify.error(msg);
    }
  };

  return (
    <div>
      <PageHeader icon={ClipboardCheck} breadcrumb="Home / Requests" title="Approved requests"
                  subtitle="Fulfil requests directly from your inventory." />

      {err && <Alert type="error">{err}</Alert>}

      {isLoading && (
        <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4"><Skeleton className="h-8 w-10" /><Skeleton className="h-4 flex-1" /><Skeleton className="h-8 w-28 rounded-xl" /></div>
          ))}
        </Card>
      )}

      {!isLoading && rows.length === 0 && (
        <EmptyState icon={Inbox} title="No approved requests" message="Approved requests awaiting fulfilment will appear here." />
      )}

      {rows.length > 0 && (
        <Table>
          <THead><TH>#</TH><TH>Group</TH><TH>Units</TH><TH>Urgency</TH><TH>Raised</TH><TH className="text-right">Action</TH></THead>
          <TBody>
            {rows.map((r) => (
              <TR key={r.id}>
                <TD className="text-slate-400">#{r.id}</TD>
                <TD><span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{r.bloodGroup}</span></TD>
                <TD className="font-semibold">{r.unitsRequired}</TD>
                <TD><UrgencyBadge urgency={r.urgency} /></TD>
                <TD className="text-slate-500">{formatDate(r.requestDate)}</TD>
                <TD className="text-right">
                  <Button size="sm" className="!px-3 !py-1.5 text-xs" loading={fulfilling} onClick={() => onFulfill(r.id)} leftIcon={<PackageCheck className="h-3.5 w-3.5" />}>
                    Fulfil from stock
                  </Button>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
    </div>
  );
}
