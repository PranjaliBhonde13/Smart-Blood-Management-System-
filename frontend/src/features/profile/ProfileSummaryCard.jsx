import { ShieldCheck } from 'lucide-react';

/**
 * Identity + completion header shown at the top of every profile page.
 * `badges` (status pills) and `completionNote` are optional extras; `photoUrl`
 * replaces the initial when the user has uploaded a photo.
 */
export default function ProfileSummaryCard({ name, email, roleLabel, completion, photoUrl, badges, completionNote }) {
  const initial = name?.charAt(0)?.toUpperCase() || '?';
  return (
    <div className="mb-6 overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900">
      <div className="h-20 bg-grad-brand" />
      <div className="flex flex-wrap items-end gap-4 px-6 pb-6">
        {photoUrl ? (
          <img src={photoUrl} alt="Your profile photo"
               className="-mt-10 h-20 w-20 shrink-0 rounded-3xl object-cover shadow-emergency ring-4 ring-white dark:ring-slate-900" />
        ) : (
          <span className="-mt-10 grid h-20 w-20 shrink-0 place-items-center rounded-3xl bg-grad-brand text-2xl font-extrabold text-white shadow-emergency ring-4 ring-white dark:ring-slate-900">{initial}</span>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h2 className="truncate text-xl font-bold text-slate-900 dark:text-white">{name || 'Your name'}</h2>
            <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-ocean-50 px-2 py-0.5 text-xs font-semibold text-ocean-600 dark:bg-ocean-900/30"><ShieldCheck className="h-3.5 w-3.5" /> {roleLabel}</span>
          </div>
          <p className="truncate text-sm text-slate-500">{email}</p>
          {badges && <div className="mt-2 flex flex-wrap gap-1.5">{badges}</div>}
        </div>
        <div className="w-full sm:w-56">
          <div className="mb-1 flex items-center justify-between text-xs font-medium text-slate-500">
            <span id="profile-completion-label">Profile completion</span><span className="font-bold text-brand-600">{completion}%</span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"
               role="progressbar" aria-labelledby="profile-completion-label"
               aria-valuenow={completion} aria-valuemin={0} aria-valuemax={100}>
            <div className="h-full rounded-full bg-grad-brand transition-all duration-500" style={{ width: `${completion}%` }} />
          </div>
          {completionNote}
        </div>
      </div>
    </div>
  );
}
