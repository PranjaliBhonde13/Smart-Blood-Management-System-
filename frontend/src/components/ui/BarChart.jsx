/**
 * Minimal dependency-free horizontal bar chart for labelled counts. Bars scale
 * to the largest value; zero-value rows still render for context. Kept simple
 * and theme-aware — good enough for admin summaries without a charting lib.
 *
 * @param {{data: Array<{label:string, value:number}>}} props
 */
export default function BarChart({ data = [] }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="space-y-3">
      {data.map((d) => (
        <div key={d.label} className="flex items-center gap-3">
          <span className="w-24 shrink-0 text-xs font-medium text-slate-500">{d.label}</span>
          <div className="h-3 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
            <div
              className="h-full rounded-full bg-grad-brand transition-all"
              style={{ width: `${(d.value / max) * 100}%` }}
            />
          </div>
          <span className="w-8 shrink-0 text-right text-sm font-semibold tabular-nums">{d.value}</span>
        </div>
      ))}
    </div>
  );
}
