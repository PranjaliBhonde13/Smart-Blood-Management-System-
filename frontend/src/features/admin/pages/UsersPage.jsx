import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Users, Search, UserPlus } from 'lucide-react';
import { useAdminUsersQuery, useSetUserEnabledMutation, useCreateAdminMutation } from '../adminApi';
import { useAuth } from '../../../hooks/useAuth';
import { parseApiError } from '../../../lib/apiError';
import { formatDate } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import { Badge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import Modal from '../../../components/ui/Modal';
import Alert from '../../../components/ui/Alert';
import EmptyState from '../../../components/ui/EmptyState';
import Pager from '../../../components/ui/Pager';

const ROLE_FILTERS = ['', 'PATIENT', 'DONOR', 'HOSPITAL', 'BLOOD_BANK', 'ADMIN'];
const STATUS_OPTIONS = [
  { value: '', label: 'Any status' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
];

/**
 * Admin user management: search by name/email/phone, filter by role and
 * status, open a user's detail page, suspend (with a reason) or restore, and
 * create further administrators.
 */
export default function UsersPage() {
  const { user: me } = useAuth();
  // ?role=DONOR (e.g. from a dashboard tile) preselects a role; unknown values are ignored.
  const [searchParams, setSearchParams] = useSearchParams();
  const [role, setRoleState] = useState(() => {
    const r = searchParams.get('role') || '';
    return ROLE_FILTERS.includes(r) ? r : '';
  });
  const setRole = (r) => {
    setRoleState(r);
    setSearchParams(r ? { role: r } : {}, { replace: true });
  };
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading, isFetching } = useAdminUsersQuery({ role, status, q, page });
  const [setEnabled, { isLoading: updating }] = useSetUserEnabledMutation();
  const [suspendTarget, setSuspendTarget] = useState(null);
  const [reason, setReason] = useState('');
  const [adminOpen, setAdminOpen] = useState(false);

  // Debounce the search box so we don't query on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => { setQ(search); setPage(0); }, 350);
    return () => clearTimeout(t);
  }, [search]);

  const rows = data?.data?.content || [];
  const total = data?.data?.totalElements;

  const enable = async (u) => {
    try {
      await setEnabled({ id: u.id, enabled: true }).unwrap();
      toast.success(`${u.email} restored`);
    } catch (err) {
      toast.error(parseApiError(err).message);
    }
  };

  const confirmSuspend = async () => {
    try {
      await setEnabled({ id: suspendTarget.id, enabled: false, reason }).unwrap();
      toast.success(`${suspendTarget.email} suspended`);
      setSuspendTarget(null);
      setReason('');
    } catch (err) {
      toast.error(parseApiError(err).message);
    }
  };

  return (
    <div>
      <PageHeader icon={Users} breadcrumb="Home / Users" title="User management"
                  subtitle="Find any account, review it, and suspend or restore access."
                  actions={<Button leftIcon={<UserPlus className="h-4 w-4" />} onClick={() => setAdminOpen(true)}>New administrator</Button>} />

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_180px]">
          <TextField label="Search" icon={Search} placeholder="Name, email or phone" value={search} onChange={(e) => setSearch(e.target.value)} />
          <Select label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} options={STATUS_OPTIONS} />
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {ROLE_FILTERS.map((r) => (
            <button key={r || 'ALL'} type="button" onClick={() => { setRole(r); setPage(0); }}
                    className={'rounded-full px-3 py-1.5 text-sm font-medium ' +
                      (role === r ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300')}>
              {r ? r.replace('_', ' ') : 'All roles'}
            </button>
          ))}
        </div>
      </Card>

      {total !== undefined && <p className="mb-2 text-sm text-slate-500">{total} account{total === 1 ? '' : 's'}{isFetching ? ' · updating…' : ''}</p>}

      {isLoading ? (
        <div className="space-y-3">{[...Array(5)].map((_, i) => <div key={i} className="skeleton h-14" />)}</div>
      ) : rows.length === 0 ? (
        <EmptyState icon={Users} title="No users found" message="Try another search or filter." />
      ) : (
        <Card className="overflow-x-auto p-0" padded={false}>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-slate-500 dark:border-slate-800">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Email verified</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Joined</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((u) => (
                <tr key={u.id} className="border-b border-slate-100 dark:border-slate-800">
                  <td className="px-4 py-3 font-medium">
                    <Link to={`/admin/users/${u.id}`} className="hover:text-brand-600 hover:underline">{u.fullName}</Link>
                  </td>
                  <td className="px-4 py-3 text-slate-500">{u.email}</td>
                  <td className="px-4 py-3"><Badge tone={u.role === 'ADMIN' ? 'brand' : 'ocean'}>{u.role.replace('_', ' ')}</Badge></td>
                  <td className="px-4 py-3">{u.emailVerified ? <Badge tone="success">Yes</Badge> : <Badge tone="neutral">No</Badge>}</td>
                  <td className="px-4 py-3">
                    <Badge tone={u.enabled ? 'success' : 'warning'} dot>{u.enabled ? 'Active' : 'Suspended'}</Badge>
                    {!u.enabled && u.suspensionReason && <div className="mt-1 max-w-[220px] truncate text-xs text-slate-400" title={u.suspensionReason}>{u.suspensionReason}</div>}
                  </td>
                  <td className="px-4 py-3 text-slate-500">{formatDate(u.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    {u.id !== me?.id && (u.enabled
                      ? <Button variant="secondary" size="sm" onClick={() => { setSuspendTarget(u); setReason(''); }}>Suspend</Button>
                      : <Button size="sm" loading={updating} onClick={() => enable(u)}>Restore</Button>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      <Pager page={page} totalPages={data?.data?.totalPages} onChange={setPage} />

      <Modal open={!!suspendTarget} onClose={() => setSuspendTarget(null)} title={`Suspend ${suspendTarget?.fullName || ''}`}
             footer={<>
               <Button variant="secondary" onClick={() => setSuspendTarget(null)}>Cancel</Button>
               <Button loading={updating} onClick={confirmSuspend}>Suspend account</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">
          {suspendTarget?.email} will be signed out immediately and can't sign in until restored.
        </p>
        <label htmlFor="suspend-reason" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Reason (shown to the user)</label>
        <textarea id="suspend-reason" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g. Duplicate account, misuse reported"
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
      </Modal>

      <CreateAdminModal open={adminOpen} onClose={() => setAdminOpen(false)} />
    </div>
  );
}

function CreateAdminModal({ open, onClose }) {
  const [createAdmin, { isLoading }] = useCreateAdminMutation();
  const empty = { fullName: '', email: '', phone: '', password: '' };
  const [form, setForm] = useState(empty);
  const [err, setErr] = useState({ message: '', fieldErrors: {} });
  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const close = () => { setForm(empty); setErr({ message: '', fieldErrors: {} }); onClose(); };

  const submit = async () => {
    setErr({ message: '', fieldErrors: {} });
    try {
      const body = { ...form, phone: form.phone || null };
      await createAdmin(body).unwrap();
      toast.success(`Administrator ${form.email} created`);
      close();
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <Modal open={open} onClose={close} title="New administrator"
           footer={<>
             <Button variant="secondary" onClick={close}>Cancel</Button>
             <Button loading={isLoading} onClick={submit}>Create administrator</Button>
           </>}>
      <p className="mb-3 text-sm text-slate-500">Administrators have full access to this panel. Share the password with them securely; they can change it with "Forgot password".</p>
      <Alert type="error">{err.message}</Alert>
      <div className="space-y-3">
        <TextField label="Full name" value={form.fullName} onChange={update('fullName')} error={err.fieldErrors.fullName} required />
        <TextField label="Email" type="email" value={form.email} onChange={update('email')} error={err.fieldErrors.email} required />
        <TextField label="Phone" value={form.phone} onChange={update('phone')} error={err.fieldErrors.phone} hint="Optional" />
        <TextField label="Temporary password" type="password" value={form.password} onChange={update('password')} error={err.fieldErrors.password} hint="8 to 72 characters." required />
      </div>
    </Modal>
  );
}
