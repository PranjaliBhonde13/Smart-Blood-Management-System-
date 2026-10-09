import Card from '../../components/ui/Card';

/** Grid class for a field that spans both columns. */
export const FULL = 'md:col-span-2';

/** Label text with a visual required marker (the input itself carries `required` for assistive tech). */
export function Label({ children, required, optional }) {
  return (
    <>
      {children}
      {required && <span className="ml-0.5 text-brand-600" aria-hidden="true">*</span>}
      {optional && <span className="ml-1 text-xs font-normal text-slate-400">(optional)</span>}
    </>
  );
}

/** A titled card grouping one part of a profile. Two columns from md up, one on phones. */
export function Section({ id, icon: Icon, title, description, children, aside }) {
  return (
    <Card className="mb-6" role="region" aria-labelledby={`${id}-title`}>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
            <Icon className="h-5 w-5" />
          </span>
          <div>
            <h2 id={`${id}-title`} className="text-lg font-bold text-slate-900 dark:text-white">{title}</h2>
            {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
          </div>
        </div>
        {aside}
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">{children}</div>
    </Card>
  );
}
