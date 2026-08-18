import { Link } from 'react-router-dom';
import { Droplet, PlusCircle, Upload, CheckCircle2, X } from 'lucide-react';
import { useMyRequestsQuery, useCancelRequestMutation, useUploadReportMutation } from '../requestsApi';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';

/** Patient's list of their own requests with status, and cancel/upload actions. */
export default function MyRequestsPage() {
  const { data, isLoading, isError } = useMyRequestsQuery();
  const [cancelRequest] = useCancelRequestMutation();
  const [uploadReport] = useUploadReportMutation();

  const rows = data?.data?.content || [];
  const CANCELLABLE = ['PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED'];

  const onUpload = (id) => (e) => {
    const file = e.target.files?.[0];
    if (file) { uploadReport({ id, file }); notify.success('Report uploaded'); }
  };

  const onCancel = (id) => { cancelRequest(id); notify.info('Request cancelled'); };

  return (
    <div>
      <PageHeader
        icon={Droplet}
        breadcrumb="Home / Requests"
        title="My requests"
        subtitle="Track the status of every request you've raised."
        actions={<Link to="/patient/requests/new"><Button leftIcon={<PlusCircle className="h-4 w-4" />}>New request</Button></Link>}
      />

      {isLoading && (
        <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4">
              <Skeleton className="h-8 w-12" /><Skeleton className="h-4 flex-1" /><Skeleton className="h-6 w-20 rounded-full" />
            </div>
          ))}
        </Card>
      )}

      {isError && <Card><p className="text-brand-600">Could not load your requests.</p></Card>}

      {!isLoading && rows.length === 0 && (
        <EmptyState
          icon={Droplet}
          title="No requests yet"
          message="When you raise a blood request, it will appear here with live status."
          action={<Link to="/patient/requests/new"><Button leftIcon={<PlusCircle className="h-4 w-4" />}>Create your first request</Button></Link>}
        />
      )}

      {rows.length > 0 && (
        <Table>
          <THead>
            <TH>Group</TH><TH>Units</TH><TH>Urgency</TH><TH>Status</TH><TH>Report</TH><TH>Raised</TH><TH className="text-right">Action</TH>
          </THead>
          <TBody>
            {rows.map((r) => (
              <TR key={r.id}>
                <TD><span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{r.bloodGroup}</span></TD>
                <TD className="font-semibold">{r.unitsRequired}</TD>
                <TD><UrgencyBadge urgency={r.urgency} /></TD>
                <TD><StatusBadge status={r.status} /></TD>
                <TD>
                  {r.medicalReport
                    ? <span className="inline-flex items-center gap-1 font-medium text-success"><CheckCircle2 className="h-4 w-4" /> Attached</span>
                    : <label className="inline-flex cursor-pointer items-center gap-1 font-medium text-brand-600 hover:underline">
                        <Upload className="h-4 w-4" /> Upload
                        <input type="file" className="hidden" accept=".pdf,.png,.jpg,.jpeg" onChange={onUpload(r.id)} />
                      </label>}
                </TD>
                <TD className="text-slate-500">{new Date(r.requestDate).toLocaleDateString()}</TD>
                <TD className="text-right">
                  {CANCELLABLE.includes(r.status) && (
                    <button onClick={() => onCancel(r.id)} className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-sm font-medium text-brand-600 transition hover:bg-brand-50 dark:hover:bg-brand-900/20">
                      <X className="h-4 w-4" /> Cancel
                    </button>
                  )}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
    </div>
  );
}
