import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Menu, Search, Sun, Moon, LogOut, UserRound, ChevronDown } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useTheme } from '../../hooks/useTheme';
import { ROLE_LABEL, ROLE_HOME } from '../../lib/constants';
import NotificationBell from '../../features/notifications/NotificationBell';

/**
 * Sticky, frosted top application bar: mobile menu button, a search field, the
 * notification bell, theme toggle, and a user menu with sign-out. Page titles
 * live in each page's header (PageHeader), keeping this bar thin.
 */
export default function Topbar({ onMenu }) {
  const { user, role, signOut } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setMenuOpen(false); };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleSignOut = () => {
    signOut();
    navigate('/login', { replace: true });
  };

  const initial = user?.fullName?.charAt(0)?.toUpperCase() || '?';

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200/70 bg-white/70 px-4 backdrop-blur-xl dark:border-slate-800 dark:bg-slate-900/70 lg:px-6">
      <button className="rounded-xl p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 lg:hidden" onClick={onMenu} aria-label="Open menu">
        <Menu className="h-5 w-5" />
      </button>

      {/* Search */}
      <div className="relative hidden max-w-md flex-1 sm:block">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input
          type="search"
          placeholder="Search requests, donors, banks…"
          className="w-full rounded-xl border border-slate-200 bg-slate-50/70 py-2.5 pl-10 pr-3 text-sm text-slate-700 outline-none transition focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-500/10 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-200"
        />
      </div>

      <div className="ml-auto flex items-center gap-1.5">
        <NotificationBell />

        <button
          onClick={toggle}
          title="Toggle theme"
          aria-label="Toggle theme"
          className="rounded-xl p-2.5 text-slate-500 transition hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
        </button>

        {/* User menu */}
        <div className="relative" ref={ref}>
          <button
            onClick={() => setMenuOpen((o) => !o)}
            className="flex items-center gap-2 rounded-xl p-1 pr-2 transition hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <span className="grid h-9 w-9 place-items-center rounded-full bg-grad-brand text-sm font-bold text-white shadow-emergency">
              {initial}
            </span>
            <span className="hidden text-left sm:block">
              <span className="block text-sm font-semibold leading-tight text-slate-700 dark:text-slate-100">{user?.fullName || 'User'}</span>
              <span className="block text-[11px] leading-tight text-slate-400">{ROLE_LABEL[role] || ''}</span>
            </span>
            <ChevronDown className={`hidden h-4 w-4 text-slate-400 transition sm:block ${menuOpen ? 'rotate-180' : ''}`} />
          </button>

          {menuOpen && (
            <div className="absolute right-0 z-40 mt-2 w-56 origin-top-right animate-scale-in overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900">
              <div className="border-b border-slate-100 px-4 py-3 dark:border-slate-800">
                <div className="text-sm font-semibold text-slate-800 dark:text-slate-100">{user?.fullName}</div>
                <div className="truncate text-xs text-slate-400">{user?.email}</div>
              </div>
              <button
                onClick={() => { setMenuOpen(false); navigate(`${ROLE_HOME[role]}/profile`); }}
                className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm text-slate-600 transition hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
              >
                <UserRound className="h-4 w-4" /> My Profile
              </button>
              <button
                onClick={handleSignOut}
                className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm font-medium text-brand-600 transition hover:bg-brand-50 dark:hover:bg-brand-900/20"
              >
                <LogOut className="h-4 w-4" /> Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
