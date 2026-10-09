import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { ScrollText, Download } from 'lucide-react';
import { useAuditLogsQuery, useAuditActionsQuery } from '../adminApi';
import { API_BASE_URL } from '../../../lib/constants';
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

const ENTITY_TYPES = ['', 'User', 'Hospital', 'BloodBank', 'BloodRequest', 'Broadcast'];
const EMPTY = { action: '', actorId: '', entityType: '', from: '', to: '' };

/** Filterable, exportable trail of administrative actions, most recent first. */
export default function AuditLogPage() {
  const token = useSelector((s) => s.auth?.accessToken);
  const [filters, setFilters] = useState(EMPTY);
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useAuditLogsQuery({ ...filters, page });
  const { data: actionsData } = useAuditActionsQuery();
  const [exporting, setExporting] = useState(false);

  const rows = data?.data?.content || [];
  const actions = actionsData?.data || [];
  const setFilter = (k) => (e) => { setFilters((f) => ({ ...f, [k]: e.target.value })); setPage(0); };
  const filtered = Object.values(filters).some(Boolean);

  const exportCsv = async () => {
    setExporting(true);
    try {
      const p = new URLSearchParams(Object.entries(filters).filter(([, v]) => v));
      const res = await fetch(`${API_BASE_URL}/admin/audit-logs/export?${p}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) throw new Error();
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Audit log exported');
    } catch {
      toast.error('Export failed — try signing in again');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <PageHeader icon={ScrollText} breadcrumb="Home / Audit Log" title="Audit log"
                  subtitle="Every administrative action, most recent first."
                  actions={<Button variant="secondary" loading={exporting} leftIcon={<Download className="h-4 w-4" />} onClick={exportCsv}>Export CSV</Button>} />

      <Card className="mb-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
          <Select label="Action" value={filters.action} onChange={setFilter('action')}
                  options={[{ value: '', label: 'All actions' }, ...actions.map((a) => ({ value: a, label: a }))]} />
          <Select label="Entity" value={filters.entityType} onChange={setFilter('entityType')}
                  options={ENTITY_TYPES.map((t) => ({ value: t, label: t || 'Any entity' }))} />
          <TextField label="Actor user #" type="number" min={1} value={filters.actorId} onChange={setFilter('actorId')} />
          <TextField label="From" type="date" value={filters.from} onChange={setFilter('from')} />
          <TextField label="To" type="date" value={filters.to} onChange={setFilter('to')} />
        </div>
        {filtered && (
          <button type="button" onClick={() => { setFilters(EMPTY); setPage(0); }} className="mt-3 text-sm font-semibold text-brand-600 hover:underline">Clear filters</button>
        )}
      </Card>

      {isLoading ? (
        <div className="space-y-3">{[...Array(6)].map((_, i) => <div key={i} className="skeleton h-12" />)}</div>
      ) : rows.length === 0 ? (
        <EmptyState icon={ScrollText} title={filtered ? 'No matching entries' : 'No activity yet'}
                    message={filtered ? 'Try widening the filters.' : 'Administrative actions will be recorded here.'} />
      ) : (
        <>
          <Card className={'overflow-x-auto p-0 ' + (isFetching ? 'opacity-60' : '')} padded={false}>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-slate-500 dark:border-slate-800">
                <tr>
                  <th className="px-4 py-3">When</th>
                  <th className="px-4 py-3">Actor</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Entity</th>
                  <th className="px-4 py-3">Details</th>
                  <th className="px-4 py-3">IP</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((a) => (
                  <tr key={a.id} className="border-b border-slate-100 dark:border-slate-800">
                    <td className="whitespace-nowrap px-4 py-3 text-slate-500">{formatDateTime(a.createdAt)}</td>
                    <td className="px-4 py-3">
                      {a.actorUserId ? <Link to={`/admin/users/${a.actorUserId}`} className="hover:text-brand-600 hover:underline">#{a.actorUserId}</Link> : '—'}
                    </td>
                    <td className="px-4 py-3"><Badge tone="ocean">{a.action}</Badge></td>
                    <td className="px-4 py-3 text-slate-500">
                      {a.entityType === 'User' && a.entityId
                        ? <Link to={`/admin/users/${a.entityId}`} className="hover:text-brand-600 hover:underline">User #{a.entityId}</Link>
                        : `${a.entityType || '—'}${a.entityId ? ` #${a.entityId}` : ''}`}
                    </td>
                    <td className="px-4 py-3">{a.details}</td>
                    <td className="px-4 py-3 text-slate-400">{a.ipAddress}</td>
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
