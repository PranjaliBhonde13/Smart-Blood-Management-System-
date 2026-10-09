import { Package, AlertTriangle, HeartHandshake } from 'lucide-react';
import { useNetworkInventoryQuery } from '../adminApi';
import { formatDate, formatDateTime } from '../../../lib/format';
import { Badge } from '../../../components/ui/Badge';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import EmptyState from '../../../components/ui/EmptyState';
import Skeleton from '../../../components/ui/Skeleton';

const GROUPS = ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'];
const LEVEL_TONE = { high: 'success', medium: 'warning', low: 'brand' };
const LEVEL_LABEL = { high: 'Healthy', medium: 'Moderate', low: 'Shortage' };

/**
 * Network-wide blood supply: totals per group across verified banks, each
 * bank's stock with low-stock and expiry warnings, and the donor pool.
 */
export default function InventoryPage() {
  const { data, isLoading } = useNetworkInventoryQuery(undefined, { pollingInterval: 60000 });
  const inv = data?.data;

  if (isLoading || !inv) {
    return (
      <div>
        <PageHeader icon={Package} breadcrumb="Home / Inventory" title="Network inventory" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{[...Array(8)].map((_, i) => <Skeleton key={i} className="h-24" />)}</div>
      </div>
    );
  }

  const shortages = inv.groups.filter((g) => g.level === 'low');
  const expiringTotal = inv.banks.reduce((n, b) => n + b.expiringSoonUnits, 0);

  return (
    <div className="space-y-6">
      <PageHeader icon={Package} breadcrumb="Home / Inventory" title="Network inventory"
                  subtitle={`Units held by verified blood banks. Low stock is ${inv.lowStockThreshold} units or fewer per group; expiry warning ${inv.expiryWarningDays} days.`} />

      {(shortages.length > 0 || expiringTotal > 0) && (
        <div className="flex flex-wrap gap-3">
          {shortages.length > 0 && (
            <div className="flex items-center gap-2 rounded-xl bg-red-50 px-4 py-2.5 text-sm font-medium text-brand-700 dark:bg-red-900/20 dark:text-red-300">
              <AlertTriangle className="h-4 w-4" /> Network shortage: {shortages.map((g) => g.bloodGroup).join(', ')}
            </div>
          )}
          {expiringTotal > 0 && (
            <div className="flex items-center gap-2 rounded-xl bg-amber-50 px-4 py-2.5 text-sm font-medium text-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
              <AlertTriangle className="h-4 w-4" /> {expiringTotal} unit(s) expire within {inv.expiryWarningDays} days
            </div>
          )}
        </div>
      )}

      {/* Per-group supply */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {inv.groups.map((g) => (
          <Card key={g.bloodGroup}>
            <div className="flex items-center justify-between">
              <span className="text-xl font-extrabold text-brand-600">{g.bloodGroup}</span>
              <Badge tone={LEVEL_TONE[g.level]}>{LEVEL_LABEL[g.level]}</Badge>
            </div>
            <div className="mt-2 text-2xl font-bold tabular-nums">{g.units}<span className="ml-1 text-sm font-medium text-slate-400">units</span></div>
            <div className="text-xs text-slate-500">at {g.banksHolding} bank{g.banksHolding === 1 ? '' : 's'}</div>
          </Card>
        ))}
      </div>

      {/* Bank matrix */}
      <Card padded={false} className="overflow-hidden">
        <div className="border-b border-slate-100 px-5 py-4 dark:border-slate-800">
          <h2 className="text-lg font-semibold">Stock by blood bank</h2>
        </div>
        {inv.banks.length === 0 ? (
          <div className="p-5"><EmptyState icon={Package} title="No blood banks yet" message="Banks appear here once they register." /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500">
                <tr className="border-b border-slate-100 dark:border-slate-800">
                  <th className="px-4 py-3">Blood bank</th>
                  {GROUPS.map((g) => <th key={g} className="px-2 py-3 text-center">{g}</th>)}
                  <th className="px-3 py-3 text-right">Total</th>
                  <th className="px-3 py-3">Expiry</th>
                  <th className="px-3 py-3">Updated</th>
                </tr>
              </thead>
              <tbody>
                {inv.banks.map((b) => (
                  <tr key={b.bankId} className="border-b border-slate-100 dark:border-slate-800">
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-800 dark:text-slate-100">{b.bankName}</div>
                      <div className="text-xs text-slate-400">{b.city || '—'} {b.verified ? '' : '· not verified'}</div>
                    </td>
                    {GROUPS.map((g) => {
                      const units = b.units[g] ?? 0;
                      const low = units <= inv.lowStockThreshold;
                      return (
                        <td key={g} className={'px-2 py-3 text-center tabular-nums ' + (units === 0 ? 'text-slate-300 dark:text-slate-600' : low ? 'font-semibold text-amber-600' : 'text-slate-700 dark:text-slate-200')}>
                          {units}
                        </td>
                      );
                    })}
                    <td className="px-3 py-3 text-right font-bold tabular-nums">{b.totalUnits}</td>
                    <td className="px-3 py-3 text-xs">
                      {b.expiredUnits > 0 && <div className="font-semibold text-brand-600">{b.expiredUnits} expired</div>}
                      {b.expiringSoonUnits > 0 && <div className="text-amber-600">{b.expiringSoonUnits} expiring soon</div>}
                      {b.nextExpiry && <div className="text-slate-400">next {formatDate(b.nextExpiry)}</div>}
                      {!b.expiredUnits && !b.expiringSoonUnits && !b.nextExpiry && <span className="text-slate-400">—</span>}
                    </td>
                    <td className="px-3 py-3 text-xs text-slate-400">{formatDateTime(b.lastUpdated)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Donor pool */}
      <Card>
        <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><HeartHandshake className="h-5 w-5 text-brand-600" /> Donor pool</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr><th className="py-2 pr-4">Group</th><th className="py-2 pr-4 text-right">Registered</th><th className="py-2 pr-4 text-right">Available</th><th className="py-2 text-right">Can donate now</th></tr>
            </thead>
            <tbody>
              {inv.donors.map((d) => (
                <tr key={d.bloodGroup} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-2 pr-4 font-bold text-brand-600">{d.bloodGroup}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{d.registered}</td>
                  <td className="py-2 pr-4 text-right tabular-nums">{d.available}</td>
                  <td className={'py-2 text-right font-semibold tabular-nums ' + (d.eligibleNow === 0 ? 'text-brand-600' : 'text-success')}>{d.eligibleNow}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-3 text-xs text-slate-400">"Can donate now" = marked available and at least 90 days since their last donation.</p>
      </Card>
    </div>
  );
}
