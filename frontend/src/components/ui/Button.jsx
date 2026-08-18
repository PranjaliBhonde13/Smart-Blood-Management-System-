import { useState, useCallback } from 'react';

/**
 * Reusable button. `variant` switches visual weight; `loading` shows a spinner
 * and disables the control so forms can't be double-submitted. Includes a
 * material-style ripple, gradient fills and hover motion. Back-compatible with
 * the original API (variant: primary | secondary | ghost, loading, className).
 *
 * Extra opt-in props: size ('sm'|'md'|'lg'), leftIcon / rightIcon, fullWidth.
 */
const VARIANTS = {
  primary:
    'bg-grad-brand text-white shadow-emergency hover:shadow-[0_14px_36px_-8px_rgba(220,38,38,0.55)] hover:brightness-[1.05]',
  secondary:
    'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 hover:border-slate-400 ' +
    'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
  ghost: 'text-brand-600 hover:bg-brand-50 dark:hover:bg-brand-900/30',
  ocean: 'bg-grad-ocean text-white shadow-glow-blue hover:brightness-[1.05]',
  success: 'bg-success text-white hover:brightness-[1.05] shadow-[0_10px_30px_-8px_rgba(22,163,74,0.5)]',
  danger: 'bg-emergency text-white hover:brightness-[1.05] shadow-emergency',
  soft: 'bg-brand-50 text-brand-700 hover:bg-brand-100 dark:bg-brand-900/25 dark:text-brand-300',
};

const SIZES = {
  sm: 'px-3.5 py-2 text-sm rounded-xl',
  md: 'px-5 py-2.5 text-sm rounded-xl',
  lg: 'px-6 py-3 text-base rounded-2xl',
};

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  leftIcon = null,
  rightIcon = null,
  fullWidth = false,
  className = '',
  onClick,
  ...props
}) {
  const [ripples, setRipples] = useState([]);

  const handleClick = useCallback(
    (e) => {
      const rect = e.currentTarget.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height);
      const id = `${e.clientX}-${e.clientY}-${rect.top}`;
      setRipples((r) => [
        ...r,
        { id, size, x: e.clientX - rect.left - size / 2, y: e.clientY - rect.top - size / 2 },
      ]);
      setTimeout(() => setRipples((r) => r.slice(1)), 600);
      onClick?.(e);
    },
    [onClick]
  );

  const base =
    'ripple-host group inline-flex select-none items-center justify-center gap-2 font-semibold ' +
    'transition-all duration-200 active:scale-[.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:active:scale-100';

  return (
    <button
      className={`${base} ${SIZES[size]} ${VARIANTS[variant] || VARIANTS.primary} ${fullWidth ? 'w-full' : ''} ${className}`}
      disabled={loading || props.disabled}
      onClick={handleClick}
      {...props}
    >
      {ripples.map((r) => (
        <span
          key={r.id}
          className="pointer-events-none absolute animate-[scale-in_.6s_ease-out] rounded-full bg-white/30"
          style={{ width: r.size, height: r.size, left: r.x, top: r.y }}
        />
      ))}
      {loading && (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-90" />
      )}
      {!loading && leftIcon}
      {children}
      {!loading && rightIcon}
    </button>
  );
}
