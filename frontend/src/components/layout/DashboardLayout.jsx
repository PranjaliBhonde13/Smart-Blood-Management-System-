import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

/**
 * Shell for all authenticated pages: persistent (collapsible) sidebar + sticky
 * top bar with a scrollable content region rendering the active route via
 * <Outlet>. A keyed motion wrapper gives every route a subtle page transition.
 */
export default function DashboardLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50 dark:bg-slate-950">
      <Sidebar
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        collapsed={collapsed}
        onToggleCollapse={() => setCollapsed((c) => !c)}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onMenu={() => setMenuOpen(true)} />
        <main className="flex-1 overflow-y-auto scroll-slim">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            className="mx-auto max-w-7xl p-5 lg:p-8"
          >
            <Outlet />
          </motion.div>
        </main>
      </div>
    </div>
  );
}
