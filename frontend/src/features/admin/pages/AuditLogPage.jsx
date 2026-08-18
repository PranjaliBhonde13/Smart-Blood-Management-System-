import { useState } from 'react';
import { useAuditLogsQuery } from '../adminApi';
import { Badge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';

/** Read-only audit trail of administrative actions (paged). */
export default function AuditLogPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useAuditLogsQuery(page);

  const rows = data?.data?.content || [];
  const totalPages = data?.data?.totalPages || 1;

  return (
    <div>
      <PageHeader breadcrumb="Home / Audit Log" title="Audit log"
                  subtitle="Every administrative action, most recent first." />

      {isLoading ? (
        <div className="space-y-3">{[...Array(6)].map((_, i) => <div key={i} className="skeleton h-12" />)}</div>
      ) : rows.length === 0 ? (
        <EmptyState icon="🗒️" title="No activity yet" message="Administrative actions will be recorded here." />
      ) : (
        <>
          <Card className="overflow-x-auto p-0" padded={false}>
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
                    <td className="px-4 py-3 text-slate-500">{new Date(a.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-3">#{a.actorUserId ?? '—'}</td>
                    <td className="px-4 py-3"><Badge tone="ocean">{a.action}</Badge></td>
                    <td className="px-4 py-3 text-slate-500">{a.entityType} #{a.entityId}</td>
                    <td className="px-4 py-3">{a.details}</td>
                    <td className="px-4 py-3 text-slate-400">{a.ipAddress}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-3">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Prev</Button>
              <span className="text-sm text-slate-500">Page {page + 1} of {totalPages}</span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
