import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Droplet, ShieldCheck, Clock, Users, HeartHandshake } from 'lucide-react';
import Logo from '../../components/ui/Logo';
import { fadeUp, stagger } from '../../lib/motion';

/**
 * Shared shell for all auth screens (login, register, OTP, reset). A premium
 * split layout: a branded medical panel on the left, a frosted-glass form card
 * on the right. API is unchanged (title, subtitle, children, footer) so every
 * auth page picks up the new look without edits.
 */
const HIGHLIGHTS = [
  { icon: Clock, label: 'Match in under a minute' },
  { icon: ShieldCheck, label: 'Verified & secure profiles' },
  { icon: Users, label: '12,000+ active donors' },
];

export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-2 dark:bg-slate-950">
      {/* Brand / illustration panel */}
      <aside className="relative hidden overflow-hidden bg-grad-brand lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-2xl" />
        <div className="pointer-events-none absolute -bottom-24 -left-16 h-96 w-96 rounded-full bg-black/10 blur-3xl" />
        <motion.div
          animate={{ y: [0, -18, 0] }}
          transition={{ duration: 7, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute right-16 top-32 grid h-20 w-20 place-items-center rounded-3xl bg-white/15 backdrop-blur"
        >
          <Droplet className="h-9 w-9 fill-white/30 text-white" />
        </motion.div>

        <Link to="/" className="relative">
          <Logo size="lg" className="[&_span]:!text-white" />
        </Link>

        <motion.div variants={stagger} initial="hidden" animate="show" className="relative">
          <motion.h2 variants={fadeUp} className="max-w-md text-4xl font-extrabold leading-tight text-white">
            Every second counts. So does every donor.
          </motion.h2>
          <motion.p variants={fadeUp} className="mt-4 max-w-md text-white/85">
            Join a real-time network that connects patients, donors, hospitals and blood
            banks — and turns emergencies into outcomes.
          </motion.p>
          <motion.ul variants={fadeUp} className="mt-8 space-y-3">
            {HIGHLIGHTS.map((h) => (
              <li key={h.label} className="flex items-center gap-3 text-white/90">
                <span className="grid h-9 w-9 place-items-center rounded-xl bg-white/15 backdrop-blur">
                  <h.icon className="h-5 w-5" />
                </span>
                <span className="font-medium">{h.label}</span>
              </li>
            ))}
          </motion.ul>
        </motion.div>

        <div className="relative flex items-center gap-2 text-sm text-white/70">
          <HeartHandshake className="h-4 w-4" /> © 2026 Smart Blood Emergency Network
        </div>
      </aside>

      {/* Form panel */}
      <main className="relative flex min-h-screen flex-col items-center justify-center px-5 py-10 sm:px-8">
        <div className="pointer-events-none absolute inset-0 bg-grad-mesh lg:hidden" />
        <div className="relative w-full max-w-md">
          <Link to="/" className="mb-8 flex justify-center lg:hidden">
            <Logo size="md" />
          </Link>

          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
            className="glass-strong rounded-4xl p-8 shadow-soft sm:p-10"
          >
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white">{title}</h1>
            {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
            <div className="mt-7">{children}</div>
          </motion.div>

          {footer && <div className="mt-6 text-center text-sm text-slate-500">{footer}</div>}
        </div>
      </main>
    </div>
  );
}
