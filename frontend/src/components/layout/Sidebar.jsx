import { NavLink } from 'react-router-dom';
import { motion } from 'framer-motion';
import { PanelLeftClose, PanelLeft } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { ROLE_LABEL } from '../../lib/constants';
import { navForRole } from './navConfig';
import Logo from '../ui/Logo';

/**
 * Left navigation rail. Shows role-appropriate links (see navConfig) and the
 * product mark. Collapses to an icon rail on desktop, and slides off-canvas on
 * mobile via the `open` prop. A shared framer-motion `layoutId` renders the
 * floating active-item indicator that glides between links.
 */
export default function Sidebar({ open, onClose, collapsed = false, onToggleCollapse }) {
  const { role } = useAuth();
  const items = navForRole(role);

  return (
    <>
      {/* Mobile backdrop */}
      {open && <div className="fixed inset-0 z-20 bg-slate-900/50 backdrop-blur-sm lg:hidden" onClick={onClose} />}

      <aside
        className={
          'fixed z-30 flex h-full shrink-0 flex-col border-r border-slate-200/70 bg-white/80 backdrop-blur-xl ' +
          'transition-all duration-300 dark:border-slate-800 dark:bg-slate-900/80 lg:static lg:translate-x-0 ' +
          (collapsed ? 'lg:w-[76px] ' : 'lg:w-64 ') +
          'w-64 ' +
          (open ? 'translate-x-0' : '-translate-x-full')
        }
      >
        {/* Brand */}
        <div className={`flex h-16 items-center border-b border-slate-200/70 dark:border-slate-800 ${collapsed ? 'justify-center px-2' : 'px-5'}`}>
          <Logo size="sm" showText={!collapsed} />
        </div>

        {!collapsed && (
          <div className="px-5 pb-2 pt-4 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400">
            {ROLE_LABEL[role] || 'Menu'}
          </div>
        )}

        <nav className={`flex-1 space-y-1 overflow-y-auto scroll-slim py-2 ${collapsed ? 'px-2' : 'px-3'}`}>
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end
                onClick={onClose}
                title={collapsed ? item.label : undefined}
                className={({ isActive }) =>
                  'group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ' +
                  (collapsed ? 'justify-center ' : '') +
                  (isActive
                    ? 'text-brand-700 dark:text-brand-300'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white')
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.span
                        layoutId="nav-active"
                        className="absolute inset-0 -z-10 rounded-xl bg-brand-50 ring-1 ring-inset ring-brand-100 dark:bg-brand-900/30 dark:ring-brand-900"
                        transition={{ type: 'spring', stiffness: 500, damping: 34 }}
                      />
                    )}
                    {isActive && !collapsed && (
                      <motion.span layoutId="nav-bar" className="absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full bg-grad-brand" />
                    )}
                    {/* Icons are React components (lucide forwardRef objects or fn);
                        render as an element. Fall back to a text node for a raw string. */}
                    {Icon
                      ? (typeof Icon === 'string'
                          ? <span>{Icon}</span>
                          : <Icon className="h-5 w-5 shrink-0" />)
                      : null}
                    {!collapsed && <span className="truncate">{item.label}</span>}
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>

        {/* Collapse toggle (desktop only) */}
        <div className="hidden border-t border-slate-200/70 p-3 dark:border-slate-800 lg:block">
          <button
            onClick={onToggleCollapse}
            className={'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-500 transition hover:bg-slate-100 dark:hover:bg-slate-800 ' + (collapsed ? 'justify-center' : '')}
          >
            {collapsed ? <PanelLeft className="h-5 w-5" /> : <><PanelLeftClose className="h-5 w-5" /> Collapse</>}
          </button>
        </div>
      </aside>
    </>
  );
}
