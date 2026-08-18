import { ChevronDown } from 'lucide-react';

/**
 * Styled select matching TextField. `options` may be strings or {value,label}.
 * Keeps native <select> semantics (accessibility, keyboard) with a custom look.
 */
export default function Select({ label, error, hint, icon: Icon = null, options = [], className = '', children, ...props }) {
  return (
    <div className={className}>
      {label && <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">{label}</span>}
      <div className="relative">
        {Icon && <Icon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />}
        <select
          className={
            'w-full appearance-none rounded-xl border bg-white/80 py-2.5 pr-10 text-slate-800 outline-none backdrop-blur transition ' +
            'focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:bg-slate-900/70 dark:text-slate-100 ' +
            (Icon ? 'pl-11 ' : 'pl-3.5 ') +
            (error ? 'border-brand-400 ' : 'border-slate-200 dark:border-slate-700 ')
          }
          {...props}
        >
          {children ||
            options.map((o) => {
              const value = typeof o === 'string' ? o : o.value;
              const text = typeof o === 'string' ? o : o.label;
              return <option key={value} value={value}>{text}</option>;
            })}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
      </div>
      {error ? (
        <span className="mt-1.5 block text-xs font-medium text-brand-600">{error}</span>
      ) : (
        hint && <span className="mt-1.5 block text-xs text-slate-400">{hint}</span>
      )}
    </div>
  );
}
