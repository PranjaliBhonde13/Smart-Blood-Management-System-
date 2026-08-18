import { Droplet } from 'lucide-react';

/**
 * Brand mark for the Smart Blood Emergency Network. A gradient blood-drop badge
 * with an optional wordmark. Used in the sidebar, auth screens and landing nav
 * so the identity is identical everywhere.
 */
export default function Logo({ size = 'md', showText = true, className = '' }) {
  const dims = {
    sm: { box: 'h-8 w-8', icon: 16, text: 'text-base' },
    md: { box: 'h-10 w-10', icon: 20, text: 'text-lg' },
    lg: { box: 'h-12 w-12', icon: 24, text: 'text-xl' },
  }[size];

  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <span
        className={`grid ${dims.box} place-items-center rounded-2xl bg-grad-brand text-white shadow-emergency`}
      >
        <Droplet size={dims.icon} className="fill-white/25" strokeWidth={2.4} />
      </span>
      {showText && (
        <span className="flex flex-col leading-none">
          <span className={`font-display font-extrabold tracking-tight text-slate-900 dark:text-white ${dims.text}`}>
            Smart Blood
          </span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-brand-600">
            Emergency Network
          </span>
        </span>
      )}
    </span>
  );
}
