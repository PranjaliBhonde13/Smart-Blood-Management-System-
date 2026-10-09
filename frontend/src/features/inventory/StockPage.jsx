import { useState } from 'react';
import { Package, Droplet, AlertTriangle, Save } from 'lucide-react';
import { useMyStockQuery, useUpsertStockMutation } from './stockApi';
import { parseApiError } from '../../lib/apiError';
import { notify } from '../../lib/toast';
import PageHeader from '../../components/layout/PageHeader';
import Card from '../../components/ui/Card';
import Button from '../../components/ui/Button';
import TextField from '../../components/ui/TextField';
import Select from '../../components/ui/Select';
import Alert from '../../components/ui/Alert';
import EmptyState from '../../components/ui/EmptyState';
import Skeleton from '../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../components/ui/Table';
import { formatDate } from '../../lib/format';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
const LOW_THRESHOLD = 5; // units at/under which a group is flagged low

/**
 * Blood bank inventory management: view current stock (with low-stock flags) and
 * upsert a group's units + expiry. One row per blood group is enforced by the
 * backend, so saving an existing group updates it.
 */
export default function StockPage() {
  const { data, isLoading } = useMyStockQuery();
  const [upsertStock, { isLoading: saving }] = useUpsertStockMutation();
  const [form, setForm] = useState({ bloodGroup: 'O+', unitsAvailable: 0, expiryDate: '' });
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const rows = data?.data || [];
  const byGroup = Object.fromEntries(rows.map((s) => [s.bloodGroup, s]));
  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    const body = {
      bloodGroup: form.bloodGroup,
      unitsAvailable: Number(form.unitsAvailable),
      ...(form.expiryDate && { expiryDate: form.expiryDate }),
    };
    try {
      await upsertStock(body).unwrap();
      notify.success(`${form.bloodGroup} stock updated`);
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <div>
      <PageHeader icon={Package} breadcrumb="Home / Inventory" title="Blood inventory"
                  subtitle="Keep units current so nearby patients can find you." />

      {/* Inventory grid — all 8 groups at a glance */}
      {!isLoading && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {BLOOD_GROUPS.map((g) => {
            const s = byGroup[g];
            const units = s?.unitsAvailable ?? 0;
            const low = units <= LOW_THRESHOLD;
            return (
              <div key={g} className={'rounded-2xl border p-4 transition ' + (s ? (low ? 'border-amber-200 bg-amber-50/50 dark:border-amber-900/40 dark:bg-amber-900/10' : 'border-slate-100 bg-white dark:border-slate-800 dark:bg-slate-900') : 'border-dashed border-slate-200 bg-slate-50/50 dark:border-slate-800 dark:bg-slate-950/30')}>
                <div className="flex items-center justify-between">
                  <span className={'grid h-9 w-9 place-items-center rounded-xl text-sm font-extrabold ' + (s ? 'bg-brand-50 text-brand-600 dark:bg-brand-900/30' : 'bg-slate-100 text-slate-400 dark:bg-slate-800')}>{g}</span>
                  {s && low && <AlertTriangle className="h-4 w-4 text-warning" />}
                </div>
                <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">{s ? units : '—'}</div>
                <div className="text-xs text-slate-500">{s ? (low ? 'Low stock' : 'units available') : 'not stocked'}</div>
              </div>
            );
          })}
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {isLoading ? (
            <Card padded={false} className="divide-y divide-slate-100 dark:divide-slate-800">
              {[...Array(4)].map((_, i) => <div key={i} className="flex items-center gap-4 p-4"><Skeleton className="h-8 w-10" /><Skeleton className="h-4 flex-1" /></div>)}
            </Card>
          ) : rows.length === 0 ? (
            <EmptyState icon={Droplet} title="No stock yet" message="Add your first blood group on the right." />
          ) : (
            <Table>
              <THead><TH>Group</TH><TH>Units</TH><TH>Expiry</TH><TH>Updated</TH></THead>
              <TBody>
                {rows.map((s) => (
                  <TR key={s.id}>
                    <TD><span className="inline-grid h-9 w-9 place-items-center rounded-xl bg-brand-50 font-bold text-brand-600 dark:bg-brand-900/30">{s.bloodGroup}</span></TD>
                    <TD>
                      <span className="font-semibold">{s.unitsAvailable}</span>
                      {s.unitsAvailable <= LOW_THRESHOLD && (
                        <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-900/30"><AlertTriangle className="h-3 w-3" /> Low</span>
                      )}
                    </TD>
                    <TD className="text-slate-500">{formatDate(s.expiryDate)}</TD>
                    <TD className="text-slate-500">{formatDate(s.lastUpdated)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </div>

        {/* Upsert form */}
        <Card>
          <h2 className="mb-4 flex items-center gap-2 text-lg font-bold text-slate-900 dark:text-white">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-900/30"><Save className="h-4 w-4" /></span>
            Update stock
          </h2>
          <Alert type="error">{err.message}</Alert>
          <form className="space-y-4" onSubmit={onSubmit}>
            <Select label="Blood group" icon={Droplet} options={BLOOD_GROUPS} value={form.bloodGroup} onChange={update('bloodGroup')} />
            <TextField label="Units available" type="number" min={0} value={form.unitsAvailable}
                       onChange={update('unitsAvailable')} error={err.fieldErrors.unitsAvailable} />
            <TextField label="Expiry date" type="date" value={form.expiryDate}
                       onChange={update('expiryDate')} error={err.fieldErrors.expiryDate} />
            <Button type="submit" fullWidth loading={saving} leftIcon={<Save className="h-4 w-4" />}>Save stock</Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
