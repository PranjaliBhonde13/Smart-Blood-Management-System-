/**
 * Labelled checkbox with optional error text tied to the input for screen
 * readers. `label` may be any node; clicking it toggles the box.
 */
export default function Checkbox({ id, label, checked, onChange, error, required = false, className = '', ...props }) {
  const describedBy = error ? `${id}-desc` : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="flex cursor-pointer items-start gap-3 text-sm text-slate-700 dark:text-slate-200">
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          aria-describedby={describedBy}
          className="mt-0.5 h-4 w-4 shrink-0 rounded accent-brand-600"
          {...props}
        />
        <span>{label}</span>
      </label>
      {error && <span id={describedBy} className="mt-1.5 block pl-7 text-xs font-medium text-brand-600">{error}</span>}
    </div>
  );
}
