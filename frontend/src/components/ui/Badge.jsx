/**
 * Small colored pills for statuses, urgency and generic tags. Colour is driven
 * by the value via lookup maps so the same visual language is used everywhere.
 */
const STATUS_STYLES = {
  PENDING: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  VERIFIED: 'bg-ocean-50 text-ocean-700 ring-ocean-200 dark:bg-ocean-900/30 dark:text-ocean-300 dark:ring-ocean-900',
  APPROVED: 'bg-indigo-50 text-indigo-700 ring-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-300 dark:ring-indigo-900',
  FULFILLED: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:ring-amber-900',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:ring-emerald-900',
  REJECTED: 'bg-red-50 text-brand-700 ring-red-200 dark:bg-red-900/30 dark:text-red-300 dark:ring-red-900',
  CANCELLED: 'bg-slate-100 text-slate-400 ring-slate-200 dark:bg-slate-800 dark:text-slate-500 dark:ring-slate-700',
};

const URGENCY_STYLES = {
  LOW: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  MEDIUM: 'bg-ocean-50 text-ocean-700 ring-ocean-200 dark:bg-ocean-900/30 dark:text-ocean-300 dark:ring-ocean-900',
  HIGH: 'bg-orange-50 text-orange-700 ring-orange-200 dark:bg-orange-900/30 dark:text-orange-300 dark:ring-orange-900',
  CRITICAL: 'bg-red-50 text-brand-700 ring-red-200 dark:bg-red-900/30 dark:text-red-300 dark:ring-red-900',
};

const TONES = {
  brand: 'bg-brand-50 text-brand-700 ring-brand-200 dark:bg-brand-900/30 dark:text-brand-300 dark:ring-brand-900',
  ocean: 'bg-ocean-50 text-ocean-700 ring-ocean-200 dark:bg-ocean-900/30 dark:text-ocean-300 dark:ring-ocean-900',
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:ring-emerald-900',
  warning: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:ring-amber-900',
  neutral: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
};

const PILL = 'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset';

/** Generic tag. `dot` adds a leading status dot; `tone` selects the colour. */
export function Badge({ children, tone = 'neutral', dot = false, className = '' }) {
  return (
    <span className={`${PILL} ${TONES[tone] || TONES.neutral} ${className}`}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  );
}

export function StatusBadge({ status }) {
  return (
    <span className={`${PILL} ${STATUS_STYLES[status] || TONES.neutral}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {status}
    </span>
  );
}

export function UrgencyBadge({ urgency }) {
  const critical = urgency === 'CRITICAL';
  return (
    <span className={`${PILL} ${URGENCY_STYLES[urgency] || TONES.neutral}`}>
      <span className={`h-1.5 w-1.5 rounded-full bg-current ${critical ? 'animate-pulse' : 'opacity-70'}`} />
      {urgency}
    </span>
  );
}
