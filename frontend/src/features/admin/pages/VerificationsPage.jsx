import {
  usePendingVerificationsQuery,
  useVerifyHospitalMutation,
  useVerifyBloodBankMutation,
} from '../adminApi';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import EmptyState from '../../../components/ui/EmptyState';

/**
 * Admin verification queue: approve hospitals and blood banks so they become
 * active/visible on the network. Verifying notifies the owner and is audited.
 */
export default function VerificationsPage() {
  const { data, isLoading } = usePendingVerificationsQuery();
  const [verifyHospital, { isLoading: vh }] = useVerifyHospitalMutation();
  const [verifyBank, { isLoading: vb }] = useVerifyBloodBankMutation();

  const hospitals = data?.data?.hospitals || [];
  const banks = data?.data?.bloodBanks || [];
  const nothing = !isLoading && hospitals.length === 0 && banks.length === 0;

  const doVerify = (fn, id, name) => async () => {
    try { await fn(id).unwrap(); toast.success(`${name} verified`); }
    catch { toast.error('Verification failed'); }
  };

  return (
    <div className="space-y-6">
      <PageHeader breadcrumb="Home / Verifications" title="Pending verifications"
                  subtitle="Approve hospitals and blood banks to activate their accounts." />

      {isLoading && <div className="space-y-3">{[...Array(3)].map((_, i) => <div key={i} className="skeleton h-16" />)}</div>}

      {nothing && <EmptyState icon="✅" title="All caught up" message="No hospitals or blood banks are awaiting verification." />}

      {hospitals.length > 0 && (
        <Card>
          <h2 className="mb-4 text-lg font-semibold">Hospitals ({hospitals.length})</h2>
          <div className="space-y-3">
            {hospitals.map((h) => (
              <div key={h.id} className="flex items-center justify-between rounded-xl border border-slate-100 p-3 dark:border-slate-800">
                <div>
                  <div className="font-semibold">{h.hospitalName}</div>
                  <div className="text-sm text-slate-500">License {h.licenseNumber} · {h.city || '—'}</div>
                </div>
                <Button size="sm" loading={vh} onClick={doVerify(verifyHospital, h.id, h.hospitalName)}>Verify</Button>
              </div>
            ))}
          </div>
        </Card>
      )}

      {banks.length > 0 && (
        <Card>
          <h2 className="mb-4 text-lg font-semibold">Blood banks ({banks.length})</h2>
          <div className="space-y-3">
            {banks.map((b) => (
              <div key={b.id} className="flex items-center justify-between rounded-xl border border-slate-100 p-3 dark:border-slate-800">
                <div>
                  <div className="font-semibold">{b.bankName}</div>
                  <div className="text-sm text-slate-500">License {b.licenseNumber} · {b.city || '—'}</div>
                </div>
                <Button size="sm" loading={vb} onClick={doVerify(verifyBank, b.id, b.bankName)}>Verify</Button>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
