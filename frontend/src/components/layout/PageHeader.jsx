/**
 * Standard page header used at the top of every screen: title, optional
 * subtitle/breadcrumb, an optional leading icon, and a right-aligned slot for
 * action buttons. Enforces the "every page has a header" UI rule.
 */
export default function PageHeader({ title, subtitle, breadcrumb, actions, icon: Icon }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="flex items-start gap-4">
        {Icon && (
          <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
            <Icon className="h-6 w-6" />
          </span>
        )}
        <div>
          {breadcrumb && <div className="mb-1 text-xs font-medium text-slate-400">{breadcrumb}</div>}
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
