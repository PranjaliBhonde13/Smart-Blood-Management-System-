import { useState, useRef, useEffect } from 'react';
import { Bell, BellRing, CheckCheck } from 'lucide-react';
import {
  useUnreadCountQuery,
  useNotificationsQuery,
  useMarkReadMutation,
  useMarkAllReadMutation,
} from './notificationsApi';

/**
 * Topbar notification bell: a live unread badge (polled) and a dropdown inbox.
 * Clicking an unread item marks it read; "Mark all read" clears the badge.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  // Poll the unread count so the badge updates without a refresh.
  const { data: countData } = useUnreadCountQuery(undefined, { pollingInterval: 20000 });
  const { data: listData } = useNotificationsQuery(0, { skip: !open });
  const [markRead] = useMarkReadMutation();
  const [markAllRead] = useMarkAllReadMutation();

  const unread = countData?.data?.count || 0;
  const items = listData?.data?.content || [];

  // Close the dropdown on outside click.
  useEffect(() => {
    const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        title="Notifications"
        aria-label="Notifications"
        className="relative rounded-xl p-2.5 text-slate-500 transition hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
      >
        {unread > 0 ? <BellRing className="h-5 w-5" /> : <Bell className="h-5 w-5" />}
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-grad-brand px-1 text-[10px] font-bold text-white ring-2 ring-white dark:ring-slate-900">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-800">
            <span className="font-semibold">Notifications</span>
            {unread > 0 && (
              <button onClick={() => markAllRead()} className="inline-flex items-center gap-1 text-xs font-semibold text-brand-600 hover:underline">
                <CheckCheck className="h-3.5 w-3.5" /> Mark all read
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            {items.length === 0 ? (
              <div className="px-4 py-8 text-center text-sm text-slate-400">No notifications yet</div>
            ) : (
              items.map((n) => (
                <button
                  key={n.id}
                  onClick={() => n.status === 'UNREAD' && markRead(n.id)}
                  className={'block w-full border-b border-slate-50 px-4 py-3 text-left last:border-0 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800 ' +
                    (n.status === 'UNREAD' ? 'bg-brand-50/40 dark:bg-brand-900/10' : '')}
                >
                  <div className="flex items-start gap-2">
                    {n.status === 'UNREAD' && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-600" />}
                    <div className={n.status === 'UNREAD' ? '' : 'pl-4'}>
                      <div className="text-sm font-semibold text-slate-700 dark:text-slate-200">{n.title}</div>
                      <div className="text-xs text-slate-500">{n.message}</div>
                      <div className="mt-0.5 text-[10px] text-slate-400">{new Date(n.createdAt).toLocaleString()}</div>
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
