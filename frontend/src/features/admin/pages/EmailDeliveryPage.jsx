import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, RotateCw, Send } from 'lucide-react';
import { useEmailLogQuery, useEmailSummaryQuery, useRetryEmailMutation, useSendEmailPreviewsMutation } from '../adminApi';
import { useAuth } from '../../../hooks/useAuth';
import { parseApiError } from '../../../lib/apiError';
import { formatDateTime } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import { Badge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import EmptyState from '../../../components/ui/EmptyState';
import Pager from '../../../components/ui/Pager';

const STATUS_TONE = { PENDING: 'warning', SENDING: 'ocean', SENT: 'success', FAILED: 'brand', SUPPRESSED: 'neutral' };
const STATUS_LABEL = {
  PENDING: 'Queued', SENDING: 'Sending', SENT: 'Sent', FAILED: 'Failed', SUPPRESSED: 'Not sent (suppressed)',
};
const CATEGORIES = ['SECURITY', 'ACCOUNT', 'VERIFICATION', 'REQUEST', 'DONATION', 'ANNOUNCEMENT'];
const EMPTY = { status: '', category: '', q: '' };
const title = (s) => s.charAt(0) + s.slice(1).toLowerCase();

/**
 * Email delivery log: every email the system queued, its delivery status and
 * any error, with a retry for failures. One-time codes are never shown.
 */
export default function EmailDeliveryPage() {
  const [filters, setFilters] = useState(EMPTY);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useEmailLogQuery({ ...filters, page }, { pollingInterval: 30000 });
  const { data: summaryData } = useEmailSummaryQuery(undefined, { pollingInterval: 30000 });
  const [retry, { isLoading: retrying, originalArgs: retryingId }] = useRetryEmailMutation();
  const [sendPreviews, { isLoading: sendingPreviews }] = useSendEmailPreviewsMutation();
  const { user } = useAuth();
  const [previewTo, setPreviewTo] = useState(user?.email || '');

  const onSendPreviews = async (e) => {
    e.preventDefault();
    try {
      const res = await sendPreviews(previewTo.trim()).unwrap();
      toast.success(res.message);
    } catch (err) {
      toast.error(parseApiError(err).message);
    }
  };

  const rows = data?.data?.content || [];
  const summary = summaryData?.data;
  const filtered = Object.values(filters).some(Boolean);
  const setFilter = (k) => (e) => { setFilters((f) => ({ ...f, [k]: e.target.value })); setPage(0); };

  const onRetry = async (id) => {
    try {
      await retry(id).unwrap();
      toast.success(`Email #${id} queued for another attempt`);
    } catch (err) {
      toast.error(parseApiError(err).message);
    }
  };

  const tiles = [
    ['PENDING', 'Queued', summary?.pending],
    ['SENT', 'Sent', summary?.sent],
    ['FAILED', 'Failed', summary?.failed],
    ['SUPPRESSED', 'Suppressed', summary?.suppressed],
  ];

  return (
    <div>
      <PageHeader icon={Mail} breadcrumb="Home / Email delivery" title="Email delivery"
                  subtitle="Every email the system sends, with its delivery status. Failed emails are retried automatically." />

      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        {tiles.map(([status, label, value]) => (
          <button key={status} type="button"
                  onClick={() => { setFilters((f) => ({ ...f, status: f.status === status ? '' : status })); setPage(0); }}
                  aria-pressed={filters.status === status}
                  className={'rounded-2xl bg-white p-4 text-left shadow-card transition hover:shadow-card-hover '
                    + (filters.status === status ? 'ring-2 ring-brand-500' : '')}>
            <span className="text-sm text-slate-500">{label}</span>
            <div className="mt-1 text-2xl font-bold text-slate-900">{value ?? '—'}</div>
          </button>
        ))}
      </div>

      <Card className="mb-4">
        <form onSubmit={onSendPreviews} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <TextField className="flex-1" label="Send template previews to" type="email" value={previewTo}
                     onChange={(e) => setPreviewTo(e.target.value)}
                     hint="One sample of every email, with sample data and '[Preview]' in the subject. Administrator accounts only." />
          <Button type="submit" variant="secondary" loading={sendingPreviews} disabled={!previewTo.trim()}
                  leftIcon={<Send className="h-4 w-4" />}>Send previews</Button>
        </form>
      </Card>

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <Select label="Status" value={filters.status} onChange={setFilter('status')}
                  options={[{ value: '', label: 'Any status' }, ...Object.keys(STATUS_LABEL).map((s) => ({ value: s, label: STATUS_LABEL[s] }))]} />
          <Select label="Type" value={filters.category} onChange={setFilter('category')}
                  options={[{ value: '', label: 'Any type' }, ...CATEGORIES.map((c) => ({ value: c, label: title(c) }))]} />
          <form onSubmit={(e) => { e.preventDefault(); setFilters((f) => ({ ...f, q: search.trim() })); setPage(0); }}>
            <TextField label="Recipient or subject" value={search} onChange={(e) => setSearch(e.target.value)}
                       placeholder="Search and press Enter" />
          </form>
        </div>
        {filtered && (
          <button type="button" onClick={() => { setFilters(EMPTY); setSearch(''); setPage(0); }}
                  className="mt-3 text-sm font-semibold text-brand-600 hover:underline">Clear filters</button>
        )}
      </Card>

      {isLoading ? (
        <div className="space-y-3">{[...Array(6)].map((_, i) => <div key={i} className="skeleton h-12" />)}</div>
      ) : rows.length === 0 ? (
        <EmptyState icon={Mail} title={filtered ? 'No matching emails' : 'No emails yet'}
                    message={filtered ? 'Try widening the filters.' : 'Emails appear here as soon as they are queued.'} />
      ) : (
        <>
          <Card className={'overflow-x-auto p-0 ' + (isFetching ? 'opacity-60' : '')} padded={false}>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-slate-500">
                <tr>
                  <th className="px-4 py-3">Queued</th>
                  <th className="px-4 py-3">Recipient</th>
                  <th className="px-4 py-3">Subject</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id} className="border-b border-slate-100 align-top">
                    <td className="whitespace-nowrap px-4 py-3 text-slate-500">{formatDateTime(e.createdAt)}</td>
                    <td className="px-4 py-3">
                      {e.userId
                        ? <Link to={`/admin/users/${e.userId}`} className="hover:text-brand-600 hover:underline">{e.recipient}</Link>
                        : e.recipient}
                    </td>
                    <td className="px-4 py-3">{e.subject}</td>
                    <td className="px-4 py-3"><Badge tone="ocean">{title(e.category)}</Badge></td>
                    <td className="px-4 py-3">
                      <Badge tone={STATUS_TONE[e.status]} dot>{STATUS_LABEL[e.status]}</Badge>
                      <div className="mt-1 text-xs text-slate-500">
                        {e.status === 'SENT' && e.sentAt && `Sent ${formatDateTime(e.sentAt)}`}
                        {e.status === 'PENDING' && e.attempts > 0 && `Attempt ${e.attempts} failed · next try ${formatDateTime(e.nextAttemptAt)}`}
                        {e.status === 'FAILED' && `${e.attempts} attempt${e.attempts === 1 ? '' : 's'}`}
                      </div>
                      {e.lastError && e.status !== 'SENT' && (
                        <div className="mt-1 max-w-xs break-words text-xs text-brand-700">{e.lastError}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {e.status === 'FAILED' && (
                        <Button size="sm" variant="secondary" loading={retrying && retryingId === e.id}
                                onClick={() => onRetry(e.id)} leftIcon={<RotateCw className="h-4 w-4" />}>Retry</Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
          <Pager page={page} totalPages={data?.data?.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
