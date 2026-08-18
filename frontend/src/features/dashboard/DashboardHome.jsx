import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Droplet, CheckCircle2, Users, HeartPulse, CalendarClock, Package,
  AlertTriangle, ClipboardList, PlusCircle, Search, Heart, ArrowRight,
  Activity, ShieldCheck, Lightbulb,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { ROLE_LABEL, ROLE_HOME } from '../../lib/constants';
import { StatCard } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { stagger, fadeUp } from '../../lib/motion';

/**
 * Role-aware landing dashboard. Shows a welcome hero, KPI tiles, quick actions
 * and helpful context per role. Figures are placeholders (—) until wired to
 * live endpoints in later modules; the layout and hierarchy are established here.
 */
const STATS_BY_ROLE = {
  PATIENT: [
    { label: 'Active requests', value: '—', icon: Droplet },
    { label: 'Completed', value: '—', icon: CheckCircle2, iconClass: 'bg-emerald-50 text-success dark:bg-emerald-900/30' },
    { label: 'Nearby donors', value: '—', icon: Users, iconClass: 'bg-ocean-50 text-ocean-600 dark:bg-ocean-900/30' },
  ],
  DONOR: [
    { label: 'Requests near you', value: '—', icon: Droplet },
    { label: 'Total donations', value: '—', icon: Heart, iconClass: 'bg-emerald-50 text-success dark:bg-emerald-900/30' },
    { label: 'Availability', value: '—', icon: Activity, iconClass: 'bg-ocean-50 text-ocean-600 dark:bg-ocean-900/30' },
  ],
  HOSPITAL: [
    { label: 'Pending verification', value: '—', icon: ShieldCheck },
    { label: 'Active cases', value: '—', icon: ClipboardList, iconClass: 'bg-ocean-50 text-ocean-600 dark:bg-ocean-900/30' },
    { label: 'Fulfilled today', value: '—', icon: CheckCircle2, iconClass: 'bg-emerald-50 text-success dark:bg-emerald-900/30' },
  ],
  BLOOD_BANK: [
    { label: 'Total units', value: '—', icon: Package },
    { label: 'Low-stock groups', value: '—', icon: AlertTriangle, iconClass: 'bg-amber-50 text-warning dark:bg-amber-900/30' },
    { label: 'Open requests', value: '—', icon: ClipboardList, iconClass: 'bg-ocean-50 text-ocean-600 dark:bg-ocean-900/30' },
  ],
  ADMIN: [
    { label: 'Total users', value: '—', icon: Users },
    { label: 'Pending approvals', value: '—', icon: ShieldCheck, iconClass: 'bg-amber-50 text-warning dark:bg-amber-900/30' },
    { label: 'Requests today', value: '—', icon: Droplet, iconClass: 'bg-ocean-50 text-ocean-600 dark:bg-ocean-900/30' },
  ],
};

/** Primary quick actions per role (routes that exist today). */
const ACTIONS_BY_ROLE = (home) => ({
  PATIENT: [
    { label: 'New blood request', to: `${home}/requests/new`, icon: PlusCircle, primary: true },
    { label: 'Find blood nearby', to: `${home}/find`, icon: Search },
    { label: 'My requests', to: `${home}/requests`, icon: Droplet },
  ],
  DONOR: [
    { label: 'View open requests', to: `${home}/open-requests`, icon: Droplet, primary: true },
    { label: 'Donation history', to: `${home}/donations`, icon: Heart },
    { label: 'My profile', to: `${home}/profile`, icon: HeartPulse },
  ],
  HOSPITAL: [
    { label: 'Request queue', to: `${home}/queue`, icon: ClipboardList, primary: true },
    { label: 'Find blood', to: `${home}/find`, icon: Search },
    { label: 'My profile', to: `${home}/profile`, icon: HeartPulse },
  ],
  BLOOD_BANK: [
    { label: 'Manage inventory', to: `${home}/inventory`, icon: Package, primary: true },
    { label: 'Incoming requests', to: `${home}/requests`, icon: ClipboardList },
    { label: 'My profile', to: `${home}/profile`, icon: HeartPulse },
  ],
  ADMIN: [{ label: 'Overview', to: home, icon: Activity, primary: true }],
});

const HEALTH_TIPS = [
  'Stay hydrated — drink extra water before and after donating.',
  'A healthy adult can safely donate whole blood every 3 months.',
  'Iron-rich foods like spinach and beans help you recover faster.',
];

export default function DashboardHome() {
  const { user, role } = useAuth();
  const home = ROLE_HOME[role] || '/';
  const stats = STATS_BY_ROLE[role] || [];
  const actions = ACTIONS_BY_ROLE(home)[role] || [];
  const firstName = user?.fullName?.split(' ')[0] || '';

  return (
    <div className="space-y-6">
      {/* Welcome hero */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className="relative overflow-hidden rounded-3xl bg-grad-brand p-7 text-white shadow-emergency sm:p-9"
      >
        <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-white/10 blur-2xl" />
        <div className="pointer-events-none absolute -bottom-20 right-24 h-56 w-56 rounded-full bg-black/10 blur-3xl" />
        <div className="relative flex flex-wrap items-center justify-between gap-4">
          <div>
            <Badge tone="neutral" className="!bg-white/15 !text-white !ring-white/20">{ROLE_LABEL[role] || 'Member'}</Badge>
            <h1 className="mt-3 text-3xl font-extrabold tracking-tight">Welcome back, {firstName} 👋</h1>
            <p className="mt-1.5 max-w-md text-white/85">Here's what's happening across your network today.</p>
          </div>
          <motion.div animate={{ scale: [1, 1.06, 1] }} transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }} className="grid h-20 w-20 place-items-center rounded-3xl bg-white/15 backdrop-blur">
            <Droplet className="h-10 w-10 fill-white/30" />
          </motion.div>
        </div>
      </motion.div>

      {/* KPI tiles */}
      <motion.div variants={stagger} initial="hidden" animate="show" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {stats.map((s) => (
          <motion.div key={s.label} variants={fadeUp}>
            <StatCard label={s.label} value={s.value} icon={s.icon} iconClass={s.iconClass} />
          </motion.div>
        ))}
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Quick actions */}
        <div className="lg:col-span-2">
          <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-4 flex items-center gap-2">
              <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-900/30"><Activity className="h-4 w-4" /></span>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">Quick actions</h2>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {actions.map((a) => (
                <Link
                  key={a.label}
                  to={a.to}
                  className={
                    'group flex items-center justify-between gap-3 rounded-2xl border p-4 transition ' +
                    (a.primary
                      ? 'border-transparent bg-grad-brand text-white shadow-emergency hover:brightness-105'
                      : 'border-slate-200 bg-slate-50/60 hover:-translate-y-0.5 hover:border-brand-200 hover:bg-white hover:shadow-card dark:border-slate-800 dark:bg-slate-950/40')
                  }
                >
                  <span className="flex items-center gap-3">
                    <span className={'grid h-10 w-10 place-items-center rounded-xl ' + (a.primary ? 'bg-white/20' : 'bg-white text-brand-600 shadow-sm dark:bg-slate-800')}>
                      <a.icon className="h-5 w-5" />
                    </span>
                    <span className={'font-semibold ' + (a.primary ? 'text-white' : 'text-slate-800 dark:text-slate-100')}>{a.label}</span>
                  </span>
                  <ArrowRight className={'h-4 w-4 transition group-hover:translate-x-1 ' + (a.primary ? 'text-white/80' : 'text-slate-400')} />
                </Link>
              ))}
            </div>
          </div>

          {/* Complete profile prompt */}
          <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-3xl border border-ocean-100 bg-ocean-50/50 p-6 dark:border-ocean-900/50 dark:bg-ocean-900/10">
            <div className="flex items-center gap-4">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-grad-ocean text-white shadow-glow-blue"><HeartPulse className="h-6 w-6" /></span>
              <div>
                <h3 className="font-bold text-slate-900 dark:text-white">Complete your profile</h3>
                <p className="text-sm text-slate-500">Accurate details help the network match you faster.</p>
              </div>
            </div>
            <Link to={`${home}/profile`} className="inline-flex items-center gap-2 rounded-xl bg-ocean-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-ocean-700">
              Complete now <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>

        {/* Side column: upcoming + health tips */}
        <div className="space-y-6">
          <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-card dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-4 flex items-center gap-2">
              <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-900/30"><CalendarClock className="h-4 w-4" /></span>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">Upcoming</h2>
            </div>
            <div className="flex flex-col items-center justify-center gap-2 py-6 text-center">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><CalendarClock className="h-6 w-6" /></span>
              <p className="text-sm text-slate-500">Nothing scheduled yet.</p>
            </div>
          </div>

          <div className="rounded-3xl border border-emerald-100 bg-emerald-50/40 p-6 dark:border-emerald-900/40 dark:bg-emerald-900/10">
            <div className="mb-4 flex items-center gap-2">
              <span className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-100 text-success dark:bg-emerald-900/40"><Lightbulb className="h-4 w-4" /></span>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">Health tips</h2>
            </div>
            <ul className="space-y-3">
              {HEALTH_TIPS.map((tip) => (
                <li key={tip} className="flex items-start gap-2.5 text-sm text-slate-600 dark:text-slate-300">
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                  {tip}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
