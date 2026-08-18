import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown } from 'lucide-react';

/**
 * Rounded, shadowed surface used across dashboards for grouping content.
 * Opt-in flags: `glass` for a frosted surface, `hover` for a lift-on-hover
 * interaction, `as` to swap the element. Back-compatible: <Card className>.
 */
export default function Card({
  children,
  className = '',
  glass = false,
  hover = false,
  padded = true,
  ...props
}) {
  const base = glass
    ? 'glass rounded-2xl shadow-soft'
    : 'rounded-2xl bg-white shadow-card dark:bg-slate-900';
  const Comp = hover ? motion.div : 'div';
  const motionProps = hover
    ? { whileHover: { y: -4 }, transition: { type: 'spring', stiffness: 300, damping: 24 } }
    : {};

  return (
    <Comp
      className={`${base} ${padded ? 'p-6' : ''} ${hover ? 'cursor-pointer transition-shadow hover:shadow-card-hover' : ''} ${className}`}
      {...motionProps}
      {...props}
    >
      {children}
    </Comp>
  );
}

/**
 * Compact KPI tile: a labelled statistic with optional icon, accent and trend.
 * Back-compatible with the original ({ label, value, accent }) signature.
 */
export function StatCard({
  label,
  value,
  accent = 'text-brand-600',
  icon: Icon = null,
  iconClass = 'bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300',
  trend = null, // { dir: 'up'|'down', value: '+12%' }
  className = '',
}) {
  const TrendIcon = trend?.dir === 'down' ? TrendingDown : TrendingUp;
  return (
    <Card hover className={`flex items-start justify-between gap-3 ${className}`}>
      <div className="min-w-0">
        <span className="text-sm font-medium text-slate-500">{label}</span>
        <div className={`mt-1 text-3xl font-bold tracking-tight ${accent}`}>{value}</div>
        {trend && (
          <span
            className={`mt-2 inline-flex items-center gap-1 text-xs font-semibold ${
              trend.dir === 'down' ? 'text-brand-600' : 'text-success'
            }`}
          >
            <TrendIcon className="h-3.5 w-3.5" />
            {trend.value}
          </span>
        )}
      </div>
      {Icon && (
        <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${iconClass}`}>
          <Icon className="h-5 w-5" />
        </span>
      )}
    </Card>
  );
}
