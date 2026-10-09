/**
 * Accessible single-choice group: a <fieldset> with a <legend> and native
 * radio inputs (arrow keys, screen readers and form semantics work as usual),
 * styled as selectable cards. `options` are { value, label, hint? }.
 */
export default function RadioGroup({
  id,
  name,
  legend,
  options,
  value,
  onChange,
  onBlur,
  error,
  hint,
  required = false,
  columns = 2,
  className = '',
}) {
  const describedBy = error || hint ? `${id}-desc` : undefined;
  const grid = columns === 3 ? 'sm:grid-cols-3' : columns === 1 ? '' : 'sm:grid-cols-2';

  return (
    <fieldset
      id={id}
      role="radiogroup"
      aria-labelledby={`${id}-legend`}
      className={className}
      aria-describedby={describedBy}
      aria-invalid={error ? true : undefined}
      aria-required={required || undefined}
    >
      <legend id={`${id}-legend`} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">{legend}</legend>
      <div className={`grid grid-cols-1 gap-2 ${grid}`}>
        {options.map((o) => (
          <label
            key={o.value}
            className={
              'flex cursor-pointer items-start gap-3 rounded-xl border bg-white px-3.5 py-2.5 text-sm transition ' +
              'hover:border-brand-300 has-[:checked]:border-brand-500 has-[:checked]:bg-brand-50/60 ' +
              'has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-brand-500/15 ' +
              'dark:bg-slate-900 dark:has-[:checked]:bg-brand-900/20 ' +
              (error ? 'border-brand-400' : 'border-slate-200 dark:border-slate-700')
            }
          >
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={value === o.value}
              onChange={() => onChange(o.value)}
              onBlur={onBlur}
              required={required}
              className="mt-0.5 h-4 w-4 shrink-0 accent-brand-600"
            />
            <span className="min-w-0">
              <span className="block font-medium text-slate-800 dark:text-slate-100">{o.label}</span>
              {o.hint && <span className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">{o.hint}</span>}
            </span>
          </label>
        ))}
      </div>
      {error ? (
        <span id={describedBy} className="mt-1.5 block text-xs font-medium text-brand-600">{error}</span>
      ) : (
        hint && <span id={describedBy} className="mt-1.5 block text-xs text-slate-500 dark:text-slate-400">{hint}</span>
      )}
    </fieldset>
  );
}
