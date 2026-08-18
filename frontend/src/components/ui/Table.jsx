/**
 * Modern data-table primitives: a rounded card wrapper with a sticky header,
 * hover rows, and comfortable spacing. Compose them like native table elements:
 *
 *   <Table>
 *     <THead><TH>Group</TH>…</THead>
 *     <TBody>
 *       <TR><TD>O+</TD>…</TR>
 *     </TBody>
 *   </Table>
 */
export function Table({ children, className = '' }) {
  return (
    <div className={`overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900 ${className}`}>
      <div className="overflow-x-auto scroll-slim">
        <table className="w-full min-w-full text-left text-sm">{children}</table>
      </div>
    </div>
  );
}

export function THead({ children }) {
  return (
    <thead className="sticky top-0 z-10 bg-slate-50/90 backdrop-blur dark:bg-slate-800/60">
      <tr className="border-b border-slate-200 dark:border-slate-700">{children}</tr>
    </thead>
  );
}

export function TH({ children, className = '' }) {
  return <th className={`px-4 py-3.5 text-xs font-semibold uppercase tracking-wide text-slate-500 ${className}`}>{children}</th>;
}

export function TBody({ children }) {
  return <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{children}</tbody>;
}

export function TR({ children, className = '' }) {
  return <tr className={`transition-colors hover:bg-slate-50/70 dark:hover:bg-slate-800/40 ${className}`}>{children}</tr>;
}

export function TD({ children, className = '' }) {
  return <td className={`px-4 py-3.5 text-slate-700 dark:text-slate-200 ${className}`}>{children}</td>;
}
