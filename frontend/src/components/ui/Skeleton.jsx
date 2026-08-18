/**
 * Skeleton loading placeholders. Use while data is fetching to avoid layout
 * shift and give the app a fast, polished feel. `.skeleton` shimmer is defined
 * in index.css.
 */
export default function Skeleton({ className = '' }) {
  return <div className={`skeleton ${className}`} />;
}

/** A card-shaped skeleton matching the StatCard footprint. */
export function SkeletonCard({ className = '' }) {
  return (
    <div className={`rounded-2xl bg-white p-6 shadow-card dark:bg-slate-900 ${className}`}>
      <Skeleton className="h-3 w-24" />
      <Skeleton className="mt-3 h-8 w-16" />
    </div>
  );
}

/** N stacked text lines — handy for list/table row placeholders. */
export function SkeletonText({ lines = 3, className = '' }) {
  return (
    <div className={`space-y-2 ${className}`}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={`h-3 ${i === lines - 1 ? 'w-2/3' : 'w-full'}`} />
      ))}
    </div>
  );
}
