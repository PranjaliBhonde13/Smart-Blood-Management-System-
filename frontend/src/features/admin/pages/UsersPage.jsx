import { useState } from 'react';
import { useAdminUsersQuery, useSetUserEnabledMutation } from '../adminApi';
import { Badge } from '../../../components/ui/Badge';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';

const ROLE_FILTERS = ['', 'PATIENT', 'DONOR', 'HOSPITAL', 'BLOOD_BANK', 'ADMIN'];

/** Admin user management: filter by role, and enable/disable (suspend) accounts. */
export default function UsersPage() {
  const [role, setRole] = useState('');
  const { data, isLoading } = useAdminUsersQuery({ role });
  const [setEnabled, { isLoading: updating }] = useSetUserEnabledMutation();

  const rows = data?.data?.content || [];

  const toggle = async (u) => {
    try {
      await setEnabled({ id: u.id, enabled: !u.enabled }).unwrap();
      toast.success(`${u.email} ${u.enabled ? 'disabled' : 'enabled'}`);
    } catch {
      toast.error('Action failed');
    }
  };

  return (
    <div>
      <PageHeader breadcrumb="Home / Users" title="User management"
                  subtitle="Suspend or restore accounts across the platform." />

      <div className="mb-4 flex flex-wrap gap-2">
        {ROLE_FILTERS.map((r) => (
          <button key={r || 'ALL'} onClick={() => setRole(r)}
                  className={'rounded-full px-3 py-1.5 text-sm font-medium ' +
                    (role === r ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300')}>
            {r ? r.replace('_', ' ') : 'All'}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="space-y-3">{[...Array(5)].map((_, i) => <div key={i} className="skeleton h-14" />)}</div>
      ) : (
        <Card className="overflow-x-auto p-0" padded={false}>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-slate-500 dark:border-slate-800">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Verified</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((u) => (
                <tr key={u.id} className="border-b border-slate-100 dark:border-slate-800">
                  <td className="px-4 py-3 font-medium">{u.fullName}</td>
                  <td className="px-4 py-3 text-slate-500">{u.email}</td>
                  <td className="px-4 py-3"><Badge tone="ocean">{u.role.replace('_', ' ')}</Badge></td>
                  <td className="px-4 py-3">{u.emailVerified ? '✅' : '—'}</td>
                  <td className="px-4 py-3">
                    <Badge tone={u.enabled ? 'success' : 'neutral'} dot>{u.enabled ? 'Active' : 'Suspended'}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    {u.role !== 'ADMIN' && (
                      <Button variant={u.enabled ? 'secondary' : 'primary'} size="sm"
                              loading={updating} onClick={() => toggle(u)}>
                        {u.enabled ? 'Disable' : 'Enable'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}
