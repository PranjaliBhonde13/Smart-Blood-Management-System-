import { Inbox } from 'lucide-react';

/**
 * Friendly placeholder shown when a list has no data yet. Optional action slot
 * keeps empty screens useful, not dead ends.
 *
 * `icon` accepts either an emoji string (legacy) or a Lucide component.
 */
export default function EmptyState({ icon = Inbox, title, message, action }) {
  // lucide icons are forwardRef *objects*, plain components are functions — both
  // are renderable. Only a string should be printed as text (emoji fallback).
  const isString = typeof icon === 'string';
  const Icon = isString ? null : icon;
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-slate-300 bg-white/50 py-16 text-center dark:border-slate-700 dark:bg-slate-900/40">
      <div className="grid h-16 w-16 place-items-center rounded-3xl bg-brand-50 text-brand-500 dark:bg-brand-900/30">
        {isString ? <span className="text-3xl">{icon}</span> : <Icon className="h-8 w-8" />}
      </div>
      <h3 className="text-lg font-semibold text-slate-700 dark:text-slate-200">{title}</h3>
      {message && <p className="max-w-sm text-sm text-slate-500">{message}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
