import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Droplet, PlusCircle, Upload, FileText, X } from 'lucide-react';
import { useMyRequestsQuery, useCancelRequestMutation, useUploadReportMutation } from '../requestsApi';
import { parseApiError } from '../../../lib/apiError';
import { openReport } from '../../../lib/openReport';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';
import Pager from '../../../components/ui/Pager';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';
import { formatDate } from '../../../lib/format';
import { BloodGroupSourceBadge, RecipientCell } from './ConfirmBloodGroupModal';

const CANCELLABLE = ['PENDING', 'VERIFIED', 'APPROVED', 'FULFILLED'];
const TERMINAL = ['COMPLETED', 'REJECTED', 'CANCELLED'];
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

/** Patient's list of their own requests with status, and cancel/upload actions. */
export default function MyRequestsPage() {
  const [page, setPage] = useState(0);
  const token = useSelector((s) => s.auth?.accessToken);
  const { data, isLoading, isError } = useMyRequestsQuery(page);
  const [cancelRequest] = useCancelRequestMutation();
  const [uploadReport] = useUploadReportMutation();
  const [busyId, setBusyId] = useState(null);

  const rows = data?.data?.content || [];

  const onUpload = (id) => async (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-selecting the same file after an error
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
      notify.error('File exceeds the 5 MB limit');
      return;
    }
    setBusyId(id);
    try {
      await uploadReport({ id, file }).unwrap();
      notify.success('Report uploaded');
    } catch (err) {
      notify.error(parseApiError(err).message);
    } finally {
      setBusyId(null);
    }
  };

  const onCancel = async (id) => {
    if (!window.confirm(`Cancel request #${id}? This cannot be undone.`)) return;
    setBusyId(id);
    try {
      await cancelRequest(id).unwrap();
      notify.info('Request cancelled');
    } catch (err) {
      notify.error(parseApiError(err).message);
    } finally {
      setBusyId(null);
    }
  };

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

      {!isLoading && !isError && rows.length === 0 && (
        <EmptyState
          icon={Droplet}
          title="No requests yet"
          message="When you raise a blood request, it will appear here with live status."
          action={<Link to="/patient/requests/new"><Button leftIcon={<PlusCircle className="h-4 w-4" />}>Create your first request</Button></Link>}
        />
      )}

      {rows.length > 0 && (
        <>
          <Table>
            <THead>
              <TH>For</TH><TH>Group</TH><TH>Units</TH><TH>Urgency</TH><TH>Status</TH><TH>Report</TH><TH>Raised</TH><TH className="text-right">Action</TH>
            </THead>
            <TBody>
              {rows.map((r) => (
                <TR key={r.id}>
                  <TD><RecipientCell r={r} /></TD>
                  <TD>
                    <span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{r.bloodGroup}</span>
                    <BloodGroupSourceBadge r={r} />
                  </TD>
                  <TD className="font-semibold">{r.unitsRequired}</TD>
                  <TD><UrgencyBadge urgency={r.urgency} /></TD>
                  <TD><StatusBadge status={r.status} /></TD>
                  <TD>
                    {r.medicalReport ? (
                      <button type="button" onClick={() => openReport(r.id, token)}
                              className="inline-flex items-center gap-1 font-medium text-success hover:underline">
                        <FileText className="h-4 w-4" /> View
                      </button>
                    ) : TERMINAL.includes(r.status) ? (
                      <span className="text-slate-400">—</span>
                    ) : (
                      <label className={'inline-flex items-center gap-1 font-medium text-brand-600 ' + (busyId === r.id ? 'opacity-60' : 'cursor-pointer hover:underline')}>
                        <Upload className="h-4 w-4" /> {busyId === r.id ? 'Uploading…' : 'Upload'}
                        <input type="file" className="hidden" accept=".pdf,.png,.jpg,.jpeg" disabled={busyId === r.id} onChange={onUpload(r.id)} />
                      </label>
                    )}
                  </TD>
                  <TD className="text-slate-500">{formatDate(r.requestDate)}</TD>
                  <TD className="text-right">
                    {CANCELLABLE.includes(r.status) && (
                      <button onClick={() => onCancel(r.id)} disabled={busyId === r.id}
                              className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-sm font-medium text-brand-600 transition hover:bg-brand-50 disabled:opacity-60 dark:hover:bg-brand-900/20">
                        <X className="h-4 w-4" /> Cancel
                      </button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
          <Pager page={page} totalPages={data?.data?.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
