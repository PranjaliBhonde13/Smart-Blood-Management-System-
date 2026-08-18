import { CheckCircle2, XCircle, Info, AlertTriangle } from 'lucide-react';

/**
 * Inline status banner for form-level success/error feedback. `type` selects the
 * colour scheme and icon; renders nothing when there's no message.
 * Back-compatible: <Alert type>{message}</Alert>. Optional `title`.
 */
const STYLES = {
  error: { cls: 'bg-red-50 text-brand-800 ring-red-200 dark:bg-red-950/40 dark:text-red-200 dark:ring-red-900/60', Icon: XCircle, accent: 'text-brand-600' },
  success: { cls: 'bg-emerald-50 text-emerald-800 ring-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-200 dark:ring-emerald-900/60', Icon: CheckCircle2, accent: 'text-success' },
  info: { cls: 'bg-ocean-50 text-ocean-800 ring-ocean-200 dark:bg-ocean-950/40 dark:text-ocean-200 dark:ring-ocean-900/60', Icon: Info, accent: 'text-ocean-600' },
  warning: { cls: 'bg-amber-50 text-amber-800 ring-amber-200 dark:bg-amber-950/40 dark:text-amber-200 dark:ring-amber-900/60', Icon: AlertTriangle, accent: 'text-warning' },
};

export default function Alert({ type = 'error', title, children }) {
  if (!children) return null;
  const { cls, Icon, accent } = STYLES[type] || STYLES.error;
  return (
    <div className={`mb-4 flex animate-fade-up items-start gap-3 rounded-xl px-4 py-3 text-sm ring-1 ring-inset ${cls}`} role="alert">
      <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${accent}`} />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div className={title ? 'mt-0.5 opacity-90' : ''}>{children}</div>
      </div>
    </div>
  );
}
