import { useState } from 'react';
import { TrendingUp } from 'lucide-react';
import { useAnalyticsQuery } from '../adminApi';
import PageHeader from '../../../components/layout/PageHeader';
import Card, { StatCard } from '../../../components/ui/Card';
import BarChart from '../../../components/ui/BarChart';
import Skeleton from '../../../components/ui/Skeleton';

const RANGES = [7, 30, 90];
// Validated pair (CVD-safe in light and dark): brand red = raised, ocean blue = completed.
const RAISED = 'bg-brand-600 dark:bg-brand-500';
const DONE = 'bg-ocean-600 dark:bg-ocean-500';

/** Request trends: volume, fulfilment, speed, mix, and per-hospital performance. */
export default function AnalyticsPage() {
  const [days, setDays] = useState(30);
  const { data, isFetching } = useAnalyticsQuery(days);
  const a = data?.data;

  return (
    <div className="space-y-6">
      <PageHeader icon={TrendingUp} breadcrumb="Home / Analytics" title="Analytics"
                  subtitle="How fast the network turns requests into delivered blood."
                  actions={
                    <div className="flex rounded-xl bg-slate-100 p-1 dark:bg-slate-800" role="group" aria-label="Time range">
                      {RANGES.map((d) => (
                        <button key={d} type="button" onClick={() => setDays(d)} aria-pressed={days === d}
                                className={'rounded-lg px-3 py-1.5 text-sm font-semibold transition ' + (days === d ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-900 dark:text-white' : 'text-slate-500')}>
                          {d} days
                        </button>
                      ))}
                    </div>
                  } />

      {!a ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">{[...Array(4)].map((_, i) => <Skeleton key={i} className="h-24" />)}</div>
      ) : (
        <div className={isFetching ? 'opacity-60 transition' : 'transition'}>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard label="Requests raised" value={a.created} />
            <StatCard label="Completed" value={a.completed} accent="text-success" />
            <StatCard label="Fulfilment rate" value={a.fulfilmentRate == null ? '—' : `${a.fulfilmentRate}%`} />
            <StatCard label="Avg. time to complete" value={a.avgHoursToComplete == null ? '—' : `${a.avgHoursToComplete} h`} />
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Fulfilment rate = completed ÷ closed (completed + rejected + cancelled). Still open: {a.open} · rejected {a.rejected} · cancelled {a.cancelled}.
          </p>

          <Card className="mt-6">
            <DailyChart series={a.series} />
          </Card>

          <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Card>
              <h2 className="mb-4 text-lg font-semibold">By urgency</h2>
              <BarChart data={Object.entries(a.byUrgency).map(([label, value]) => ({ label, value }))} />
            </Card>
            <Card>
              <h2 className="mb-4 text-lg font-semibold">By blood group</h2>
              <BarChart data={Object.entries(a.byBloodGroup).map(([label, value]) => ({ label, value }))} />
            </Card>
          </div>

          <Card className="mt-6" padded={false}>
            <h2 className="px-5 pt-5 text-lg font-semibold">Hospital performance</h2>
            {a.hospitals.length === 0 ? (
              <p className="px-5 pb-5 pt-2 text-sm text-slate-500">No hospital has handled a request in this period.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="mt-3 w-full text-left text-sm">
                  <thead className="text-slate-500">
                    <tr className="border-b border-slate-100 dark:border-slate-800">
                      <th className="px-5 py-2">Hospital</th>
                      <th className="px-3 py-2 text-right">Handled</th>
                      <th className="px-3 py-2 text-right">Completed</th>
                      <th className="px-3 py-2 text-right">Rejected</th>
                      <th className="px-5 py-2 text-right">Avg. hours to complete</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.hospitals.map((h) => (
                      <tr key={h.hospitalId} className="border-b border-slate-100 last:border-0 dark:border-slate-800">
                        <td className="px-5 py-2.5 font-medium">{h.hospitalName}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{h.handled}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{h.completed}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{h.rejected}</td>
                        <td className="px-5 py-2.5 text-right tabular-nums">{h.avgHoursToComplete ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}

/**
 * Paired daily columns (raised vs completed) on one shared y-scale, with a
 * legend, faint gridlines and a hover readout for the focused day.
 */
function DailyChart({ series }) {
  const [focus, setFocus] = useState(null);
  const max = Math.max(1, ...series.map((d) => Math.max(d.created, d.completed)));
  const ticks = [max, Math.round(max / 2), 0].filter((v, i, arr) => arr.indexOf(v) === i);
  const shown = focus ?? series[series.length - 1];
  const fmt = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
  const labelEvery = Math.ceil(series.length / 8);

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-lg font-semibold">Requests per day</h2>
        <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-slate-300">
          <span className="inline-flex items-center gap-1.5"><span className={`h-2.5 w-2.5 rounded-sm ${RAISED}`} /> Raised</span>
          <span className="inline-flex items-center gap-1.5"><span className={`h-2.5 w-2.5 rounded-sm ${DONE}`} /> Completed</span>
        </div>
      </div>

      <p className="mb-2 h-5 text-sm text-slate-500" aria-live="polite">
        {shown && <><b className="text-slate-800 dark:text-slate-100">{fmt(shown.date)}</b> · {shown.created} raised · {shown.completed} completed</>}
      </p>

      <div className="flex gap-2">
        {/* y-axis */}
        <div className="relative h-48 w-6 shrink-0 text-right text-[10px] tabular-nums text-slate-400">
          {ticks.map((t) => (
            <span key={t} className="absolute right-0 -translate-y-1/2" style={{ top: `${100 - (t / max) * 100}%` }}>{t}</span>
          ))}
        </div>
        <div className="min-w-0 flex-1">
          <div className="relative h-48">
            {ticks.map((t) => (
              <div key={t} className="absolute inset-x-0 border-t border-slate-100 dark:border-slate-800" style={{ top: `${100 - (t / max) * 100}%` }} />
            ))}
            <div className="absolute inset-0 flex items-end" onMouseLeave={() => setFocus(null)}>
              {series.map((d) => (
                <div key={d.date} className={'flex h-full flex-1 items-end justify-center gap-[2px] px-[1px] ' + (focus?.date === d.date ? 'bg-slate-100/70 dark:bg-slate-800/50' : '')}
                     onMouseEnter={() => setFocus(d)} title={`${fmt(d.date)}: ${d.created} raised, ${d.completed} completed`}>
                  <div className={`w-full max-w-[10px] rounded-t ${RAISED}`} style={{ height: `${(d.created / max) * 100}%` }} />
                  <div className={`w-full max-w-[10px] rounded-t ${DONE}`} style={{ height: `${(d.completed / max) * 100}%` }} />
                </div>
              ))}
            </div>
          </div>
          <div className="mt-1 flex text-[10px] text-slate-400">
            {series.map((d, i) => (
              <span key={d.date} className="flex-1 truncate text-center">{i % labelEvery === 0 ? fmt(d.date) : ''}</span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
