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
  const fieldId = id || (label ? `f-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined);

  return (
    <div className={className}>
      <div className="relative">
        {Icon && (
          <Icon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400 transition-colors peer-focus:text-brand-500" />
        )}
        <input
          id={fieldId}
          type={inputType}
          placeholder=" "
          className={
            'peer w-full rounded-xl border bg-white/80 pt-5 pb-2 text-slate-800 outline-none backdrop-blur transition ' +
            'focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 ' +
            'dark:bg-slate-900/70 dark:text-slate-100 ' +
            (Icon ? 'pl-11 pr-3.5 ' : 'px-3.5 ') +
            (isPassword ? 'pr-11 ' : '') +
            (error
              ? 'border-brand-400 focus:border-brand-500 focus:ring-brand-500/20 '
              : 'border-slate-200 dark:border-slate-700 ')
          }
          {...props}
        />
        {label && (
          <label
            htmlFor={fieldId}
            className={
              'pointer-events-none absolute top-1/2 -translate-y-1/2 text-slate-400 transition-all duration-200 ' +
              (Icon ? 'left-11 ' : 'left-3.5 ') +
              'peer-focus:top-2.5 peer-focus:translate-y-0 peer-focus:text-xs peer-focus:font-semibold peer-focus:text-brand-600 ' +
              'peer-[:not(:placeholder-shown)]:top-2.5 peer-[:not(:placeholder-shown)]:translate-y-0 ' +
              'peer-[:not(:placeholder-shown)]:text-xs peer-[:not(:placeholder-shown)]:font-medium ' +
              (Icon ? 'peer-focus:left-3.5 peer-[:not(:placeholder-shown)]:left-3.5 ' : '')
            }
          >
            {label}
          </label>
        )}
        {isPassword && (
          <button
            type="button"
            tabIndex={-1}
            onClick={() => setShow((s) => !s)}
            aria-label={show ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-1/2 -translate-y-1/2 rounded-lg p-1 text-slate-400 transition hover:text-slate-600 dark:hover:text-slate-200"
          >
            {show ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
          </button>
        )}
      </div>
      {error ? (
        <span className="mt-1.5 block animate-fade-up text-xs font-medium text-brand-600">{error}</span>
      ) : (
        hint && <span className="mt-1.5 block text-xs text-slate-400">{hint}</span>
      )}
    </div>
  );
}
