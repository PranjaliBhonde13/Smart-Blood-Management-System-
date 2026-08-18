import { useEffect, useState } from 'react';

/**
 * Minimal, dependency-free toast system.
 *
 * - `toast.success/error/info(message)` enqueues a transient notification.
 * - `<AppToaster />` (mounted once in App) renders the stack, top-right.
 *
 * A module-level listener set decouples the emitter from the renderer, so any
 * component can call `toast.*` without prop-drilling or context.
 */
let listeners = new Set();
let seq = 0;

function emit(type, message, ms = 3500) {
  const item = { id: ++seq, type, message };
  listeners.forEach((l) => l(item, ms));
}

export const toast = {
  success: (m, ms) => emit('success', m, ms),
  error: (m, ms) => emit('error', m, ms),
  info: (m, ms) => emit('info', m, ms),
};

/** Alias — some modules import { notify } instead of { toast }. Same store. */
export const notify = toast;

const STYLES = {
  success: 'bg-emerald-600',
  error: 'bg-red-600',
  info: 'bg-slate-800',
};

/** Renders and auto-dismisses queued toasts. Mount once at the app root. */
export function AppToaster() {
  const [items, setItems] = useState([]);

  useEffect(() => {
    const add = (item, ms) => {
      setItems((cur) => [...cur, item]);
      setTimeout(() => setItems((cur) => cur.filter((i) => i.id !== item.id)), ms);
    };
    listeners.add(add);
    return () => listeners.delete(add);
  }, []);

  return (
    <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
      {items.map((i) => (
        <div
          key={i.id}
          className={`pointer-events-auto animate-fade-up rounded-xl px-4 py-3 text-sm font-medium text-white shadow-card ${STYLES[i.type] || STYLES.info}`}
        >
          {i.message}
        </div>
      ))}
    </div>
  );
}
