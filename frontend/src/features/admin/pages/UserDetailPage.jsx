import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, UserRound, LogOut, KeyRound, ShieldOff, ShieldCheck } from 'lucide-react';
import {
  useUserDetailQuery, useSetUserEnabledMutation, useForceLogoutMutation, useSendPasswordResetMutation,
} from '../adminApi';
import { useAuth } from '../../../hooks/useAuth';
import { parseApiError } from '../../../lib/apiError';
import { formatDate, formatDateTime } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import { Badge, StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Alert from '../../../components/ui/Alert';
import Skeleton from '../../../components/ui/Skeleton';

/** One user's profile, numbers, history and admin actions on a single page. */
export default function UserDetailPage() {
  const { id } = useParams();
  const { user: me } = useAuth();
  const { data, isLoading, isError, error } = useUserDetailQuery(id);
  const [setEnabled, { isLoading: toggling }] = useSetUserEnabledMutation();
  const [forceLogout, { isLoading: loggingOut }] = useForceLogoutMutation();
  const [sendReset, { isLoading: resetting }] = useSendPasswordResetMutation();
  const [suspendOpen, setSuspendOpen] = useState(false);
  const [reason, setReason] = useState('');

  const d = data?.data;
  const u = d?.user;
  const isSelf = u && me && u.id === me.id;

  const act = async (fn, ok) => {
    try {
      await fn().unwrap();
      toast.success(ok);
      return true;
    } catch (err) {
      toast.error(parseApiError(err).message);
      return false;
    }
  };

  if (isLoading) {
    return <div className="space-y-4"><Skeleton className="h-28" /><Skeleton className="h-64" /></div>;
  }
  if (isError || !u) {
    return (
      <div>
        <BackLink />
        <Alert type="error">{parseApiError(error).message || 'User not found'}</Alert>
      </div>
    );
  }

  const profileEntries = Object.entries(d.profile || {});
  const statEntries = Object.entries(d.stats || {});

  return (
    <div className="space-y-6">
      <BackLink />

      {/* Header */}
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-4">
            <span className="grid h-14 w-14 place-items-center rounded-2xl bg-grad-brand text-xl font-extrabold text-white">
              {u.fullName?.charAt(0)?.toUpperCase() || <UserRound className="h-6 w-6" />}
            </span>
            <div>
              <h1 className="text-xl font-bold text-slate-900 dark:text-white">{u.fullName}</h1>
              <p className="text-sm text-slate-500">{u.email}{u.phone ? ` · ${u.phone}` : ''}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                <Badge tone={u.role === 'ADMIN' ? 'brand' : 'ocean'}>{u.role.replace('_', ' ')}</Badge>
                <Badge tone={u.enabled ? 'success' : 'warning'} dot>{u.enabled ? 'Active' : 'Suspended'}</Badge>
                <Badge tone={u.emailVerified ? 'success' : 'neutral'}>{u.emailVerified ? 'Email verified' : 'Email not verified'}</Badge>
                <span className="text-xs text-slate-400 self-center">Joined {formatDate(u.createdAt)}</span>
              </div>
            </div>
          </div>
          {!isSelf && (
            <div className="flex flex-wrap gap-2">
              {u.enabled
                ? <Button variant="secondary" size="sm" leftIcon={<ShieldOff className="h-4 w-4" />} onClick={() => { setReason(''); setSuspendOpen(true); }}>Suspend</Button>
                : <Button size="sm" loading={toggling} leftIcon={<ShieldCheck className="h-4 w-4" />} onClick={() => act(() => setEnabled({ id: u.id, enabled: true }), 'Account restored')}>Restore</Button>}
              <Button variant="secondary" size="sm" loading={loggingOut} leftIcon={<LogOut className="h-4 w-4" />}
                      onClick={() => window.confirm(`Sign ${u.fullName} out on every device?`) && act(() => forceLogout(u.id), 'Signed out everywhere')}>
                Force sign-out
              </Button>
              <Button variant="secondary" size="sm" loading={resetting} leftIcon={<KeyRound className="h-4 w-4" />}
                      onClick={() => act(() => sendReset(u.id), `Reset code sent to ${u.email}`)}>
                Send password reset
              </Button>
            </div>
          )}
        </div>
        {!u.enabled && u.suspensionReason && (
          <div className="mt-4"><Alert type="error" title="Suspension reason">{u.suspensionReason}</Alert></div>
        )}
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-3 text-lg font-semibold">Profile</h2>
          {profileEntries.length === 0 ? (
            <p className="text-sm text-slate-500">This user hasn't completed their profile yet.</p>
          ) : (
            <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
              {profileEntries.map(([k, v]) => (
                <div key={k} className="min-w-0">
                  <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{k}</dt>
                  <dd className="break-words text-sm text-slate-800 dark:text-slate-100">{v === null || v === '' ? '—' : String(v)}</dd>
                </div>
              ))}
            </dl>
          )}
        </Card>
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Numbers</h2>
          {statEntries.length === 0 ? <p className="text-sm text-slate-500">No activity yet.</p> : (
            <div className="space-y-3">
              {statEntries.map(([k, v]) => (
                <div key={k} className="flex items-center justify-between">
                  <span className="text-sm text-slate-500">{k}</span>
                  <span className="text-lg font-bold tabular-nums">{v}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {d.recentRequests?.length > 0 && (
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Recent requests</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500"><tr><th className="py-2 pr-4">#</th><th className="py-2 pr-4">Need</th><th className="py-2 pr-4">Urgency</th><th className="py-2 pr-4">Status</th><th className="py-2">Raised</th></tr></thead>
              <tbody>
                {d.recentRequests.map((r) => (
                  <tr key={r.id} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="py-2 pr-4 text-slate-400">#{r.id}</td>
                    <td className="py-2 pr-4"><b className="text-brand-600">{r.bloodGroup}</b> × {r.unitsRequired}</td>
                    <td className="py-2 pr-4"><UrgencyBadge urgency={r.urgency} /></td>
                    <td className="py-2 pr-4"><StatusBadge status={r.status} /></td>
                    <td className="py-2 text-slate-500">{formatDateTime(r.requestDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {d.recentDonations?.length > 0 && (
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Donations</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500"><tr><th className="py-2 pr-4">Request</th><th className="py-2 pr-4">Units</th><th className="py-2 pr-4">Status</th><th className="py-2">Date</th></tr></thead>
              <tbody>
                {d.recentDonations.map((x) => (
                  <tr key={x.id} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="py-2 pr-4">#{x.requestId}</td>
                    <td className="py-2 pr-4">{x.unitsDonated}</td>
                    <td className="py-2 pr-4"><Badge tone={x.status === 'COMPLETED' ? 'success' : x.status === 'SCHEDULED' ? 'warning' : 'neutral'}>{x.status}</Badge></td>
                    <td className="py-2 text-slate-500">{formatDate(x.donationDate || x.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card>
        <h2 className="mb-3 text-lg font-semibold">Activity</h2>
        {d.activity?.length ? (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {d.activity.map((a) => (
              <li key={a.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2.5 text-sm">
                <span className="min-w-0"><Badge tone="ocean">{a.action}</Badge> <span className="ml-1 text-slate-600 dark:text-slate-300">{a.details}</span></span>
                <span className="text-xs text-slate-400">{formatDateTime(a.createdAt)}</span>
              </li>
            ))}
          </ul>
        ) : <p className="text-sm text-slate-500">No administrative activity recorded for this user.</p>}
      </Card>

      <Modal open={suspendOpen} onClose={() => setSuspendOpen(false)} title={`Suspend ${u.fullName}`}
             footer={<>
               <Button variant="secondary" onClick={() => setSuspendOpen(false)}>Cancel</Button>
               <Button loading={toggling} onClick={async () => {
                 if (await act(() => setEnabled({ id: u.id, enabled: false, reason }), 'Account suspended')) setSuspendOpen(false);
               }}>Suspend account</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">They will be signed out immediately and can't sign in until restored.</p>
        <label htmlFor="detail-suspend-reason" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Reason (shown to the user)</label>
        <textarea id="detail-suspend-reason" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
      </Modal>
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/admin/users" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-brand-600">
      <ArrowLeft className="h-4 w-4" /> All users
    </Link>
  );
}
