import { Droplet, HeartHandshake, Check, X, Calendar, Sparkles } from 'lucide-react';
import { useOpenRequestsQuery } from '../../requests/requestsApi';
import { useAcceptRequestMutation, useRejectRequestMutation } from '../donationsApi';
import { notify } from '../../../lib/toast';
import { StatusBadge, UrgencyBadge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';
import { SkeletonCard } from '../../../components/ui/Skeleton';

/**
 * Donor's feed of approved requests they are blood-compatible to fulfil.
 * Accepting schedules a donation and notifies the patient/hospital; declining
 * leaves the request open for other donors.
 */
export default function OpenRequestsPage() {
  const { data, isLoading } = useOpenRequestsQuery();
  const [accept, { isLoading: accepting }] = useAcceptRequestMutation();
  const [reject, { isLoading: rejecting }] = useRejectRequestMutation();

  const rows = data?.data?.content || [];
  const busy = accepting || rejecting;

  const onAccept = (id) => accept(id).unwrap().then(() => notify.success('Thank you! Donation scheduled 🎉'));
  const onReject = (id) => reject(id).unwrap().then(() => notify.info('Request declined'));

  return (
    <div>
      <PageHeader icon={HeartHandshake} breadcrumb="Home / Open Requests" title="Requests you can help with"
                  subtitle="Approved requests compatible with your blood group." />

      {isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[...Array(3)].map((_, i) => <SkeletonCard key={i} className="h-44" />)}
        </div>
      )}

      {!isLoading && rows.length === 0 && (
        <EmptyState icon={Sparkles} title="Nothing right now"
                    message="There are no open requests matching your blood group. We'll notify you when one appears." />
      )}

      {rows.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((r) => (
            <Card key={r.id} hover className="flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <span className="grid h-14 w-14 place-items-center rounded-2xl bg-grad-brand text-xl font-extrabold text-white shadow-emergency">
                  {r.bloodGroup}
                </span>
                <UrgencyBadge urgency={r.urgency} />
              </div>
              <div className="space-y-1.5 text-sm">
                <div className="flex items-center gap-2 text-slate-700 dark:text-slate-200">
                  <Droplet className="h-4 w-4 text-brand-500" />
                  <span className="font-semibold">{r.unitsRequired} unit(s)</span> needed
                </div>
                <div className="flex items-center gap-2 text-slate-500">
                  <Calendar className="h-4 w-4" /> Raised {new Date(r.requestDate).toLocaleDateString()}
                </div>
                <StatusBadge status={r.status} />
              </div>
              <div className="mt-auto flex gap-2">
                <Button fullWidth size="sm" loading={busy} onClick={() => onAccept(r.id)} leftIcon={<Check className="h-4 w-4" />}>Accept</Button>
                <Button variant="secondary" size="sm" loading={busy} onClick={() => onReject(r.id)} leftIcon={<X className="h-4 w-4" />}>Decline</Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
