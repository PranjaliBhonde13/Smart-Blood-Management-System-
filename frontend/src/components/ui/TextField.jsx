import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

/**
 * Labelled input with a floating label, focus glow, optional leading icon and
 * an automatic show/hide toggle for password fields. Errors animate in below.
 * Back-compatible: <TextField label error type value onChange required />.
 *
 * Extra opt-in props: `icon` (Lucide component), `hint` (helper text).
 */
export default function TextField({
  label,
  error,
  hint,
  icon: Icon = null,
  type = 'text',
  className = '',
  id,
  ...props
}) {
  const [show, setShow] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword ? (show ? 'text' : 'password') : type;
  const fieldId = id || (typeof label === 'string' ? `f-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined);
  // Tie the error/hint text to the input for screen readers.
  const describedBy = fieldId && (error || hint) ? `${fieldId}-desc` : undefined;

  return (
    <div className={className}>
      {label && (
        <label htmlFor={fieldId} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">
          {label}
        </label>
      )}
      <div className="relative">
        <input
          id={fieldId}
          type={inputType}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={
            'w-full rounded-xl border bg-white py-2.5 text-slate-800 outline-none transition ' +
            'focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 ' +
            'dark:bg-slate-900 dark:text-slate-100 ' +
            (Icon ? 'pl-11 pr-3.5 ' : 'px-3.5 ') +
            (isPassword ? 'pr-11 ' : '') +
            (error
              ? 'border-brand-400 focus:border-brand-500 focus:ring-brand-500/20 '
              : 'border-slate-200 dark:border-slate-700 ')
          }
          {...props}
        />
        {Icon && (
          <Icon className="pointer-events-none absolute left-3.5 top-1/2 z-10 h-5 w-5 -translate-y-1/2 text-slate-500 dark:text-slate-400" />
        )}
        {isPassword && (
          <button
            type="button"
            tabIndex={-1}
            onClick={() => setShow((s) => !s)}
            aria-label={show ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-1/2 z-10 -translate-y-1/2 rounded-lg p-1 text-slate-500 transition hover:text-slate-800 dark:text-slate-400 dark:hover:text-white"
          >
            {show ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
          </button>
        )}
      </div>
      {error ? (
        <span id={describedBy} className="mt-1.5 block animate-fade-up text-xs font-medium text-brand-600">{error}</span>
      ) : (
        hint && <span id={describedBy} className="mt-1.5 block text-xs text-slate-500 dark:text-slate-400">{hint}</span>
      )}
    </div>
  );
}
