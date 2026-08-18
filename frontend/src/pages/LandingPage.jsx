import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Droplet, Siren, HeartHandshake, MapPin, ShieldCheck, Clock, Bell, Activity,
  Hospital, Users, Search, ArrowRight, ChevronDown, Star, Phone, Mail, Menu, X,
} from 'lucide-react';
import Logo from '../components/ui/Logo';
import Button from '../components/ui/Button';
import { fadeUp, stagger } from '../lib/motion';

/* Illustrative marketing data — no backend calls on the public page. */
const STATS = [
  { value: '12,400+', label: 'Registered donors' },
  { value: '3,200+', label: 'Lives supported' },
  { value: '48s', label: 'Avg. match time' },
  { value: '320+', label: 'Partner hospitals' },
];

const BLOOD_GROUPS = [
  { group: 'O+', level: 'high' }, { group: 'O-', level: 'low' },
  { group: 'A+', level: 'high' }, { group: 'A-', level: 'medium' },
  { group: 'B+', level: 'medium' }, { group: 'B-', level: 'low' },
  { group: 'AB+', level: 'high' }, { group: 'AB-', level: 'low' },
];

const STEPS = [
  { icon: Siren, title: 'Raise a request', text: 'Patients or hospitals post an emergency request with blood group, units and location in seconds.' },
  { icon: Search, title: 'We match instantly', text: 'Our network finds compatible, nearby donors and blood banks and alerts them in real time.' },
  { icon: HeartHandshake, title: 'Get fulfilled fast', text: 'Track responders live, confirm donation, and close the loop — often in minutes.' },
];

const FEATURES = [
  { icon: Clock, title: 'Real-time matching', text: 'Requests are broadcast to eligible donors the moment they are created.' },
  { icon: MapPin, title: 'Location-aware search', text: 'Find the closest compatible donors and banks with distance and availability.' },
  { icon: ShieldCheck, title: 'Verified & secure', text: 'Verified profiles, role-based access and privacy-first data handling.' },
  { icon: Bell, title: 'Smart notifications', text: 'Emergency alerts, donation reminders and approval updates in one place.' },
  { icon: Activity, title: 'Live inventory', text: 'Blood banks keep stock levels current so patients see what is truly available.' },
  { icon: Hospital, title: 'Hospital workflows', text: 'Dedicated queues and verification tools for hospitals and blood banks.' },
];

const TESTIMONIALS = [
  { name: 'Dr. Anita Rao', role: 'Emergency Physician', text: 'We fulfilled a critical AB- request in under ten minutes. This network is a genuine lifesaver.' },
  { name: 'Rahul Mehta', role: 'Regular Donor', text: 'I get alerts only when I am truly needed nearby. Donating has never felt this meaningful.' },
  { name: 'City Care Blood Bank', role: 'Partner Bank', text: 'Live inventory cut our phone calls dramatically. Requests now reach us instantly.' },
];

const FAQS = [
  { q: 'Is Smart Blood Emergency Network free to use?', a: 'Yes. Patients, donors, hospitals and blood banks can join and use the core matching network at no cost.' },
  { q: 'How fast are emergency requests matched?', a: 'Requests are broadcast instantly to compatible, nearby donors and banks. Average match times are under a minute.' },
  { q: 'How do you keep my data safe?', a: 'We use verified profiles, role-based access control and privacy-first handling. Your contact details are only shared when you choose to respond.' },
  { q: 'Who can donate blood?', a: 'Generally healthy adults who meet weight and eligibility criteria and have observed the required gap since their last donation.' },
];

const LEVEL = {
  high: { label: 'Available', cls: 'text-success', bar: 'bg-success', w: 'w-[85%]' },
  medium: { label: 'Moderate', cls: 'text-warning', bar: 'bg-warning', w: 'w-1/2' },
  low: { label: 'Urgent need', cls: 'text-brand-600', bar: 'bg-brand-500', w: 'w-1/4' },
};

/** Scroll-reveal wrapper. */
function Reveal({ children, className = '', delay = 0 }) {
  return (
    <motion.div
      variants={fadeUp}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: '-80px' }}
      transition={{ delay }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

function Nav() {
  const [open, setOpen] = useState(false);
  const links = [
    ['How it works', '#how'],
    ['Features', '#features'],
    ['Availability', '#availability'],
    ['FAQ', '#faq'],
  ];
  return (
    <header className="sticky top-0 z-50">
      <div className="glass border-b border-white/40 dark:border-white/5">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-3.5 lg:px-8">
          <Link to="/"><Logo /></Link>
          <nav className="hidden items-center gap-1 md:flex">
            {links.map(([label, href]) => (
              <a key={href} href={href} className="rounded-lg px-3.5 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800">
                {label}
              </a>
            ))}
          </nav>
          <div className="hidden items-center gap-2 md:flex">
            <Link to="/login"><Button variant="secondary" size="sm">Login</Button></Link>
            <Link to="/register"><Button size="sm">Get started</Button></Link>
          </div>
          <button className="rounded-lg p-2 text-slate-600 md:hidden dark:text-slate-300" onClick={() => setOpen((o) => !o)} aria-label="Menu">
            {open ? <X /> : <Menu />}
          </button>
        </div>
        {open && (
          <div className="border-t border-white/40 px-5 py-3 md:hidden dark:border-white/5">
            {links.map(([label, href]) => (
              <a key={href} href={href} onClick={() => setOpen(false)} className="block rounded-lg px-3 py-2.5 text-sm font-medium text-slate-700 dark:text-slate-200">{label}</a>
            ))}
            <div className="mt-2 flex gap-2">
              <Link to="/login" className="flex-1"><Button variant="secondary" fullWidth size="sm">Login</Button></Link>
              <Link to="/register" className="flex-1"><Button fullWidth size="sm">Get started</Button></Link>
            </div>
          </div>
        )}
      </div>
    </header>
  );
}

function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-grad-mesh" />
      <div className="pointer-events-none absolute -top-24 right-0 h-96 w-96 rounded-full bg-brand-200/40 blur-3xl dark:bg-brand-900/20" />
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 py-16 lg:grid-cols-2 lg:gap-8 lg:px-8 lg:py-24">
        <motion.div variants={stagger} initial="hidden" animate="show">
          <motion.div variants={fadeUp}>
            <span className="inline-flex items-center gap-2 rounded-full bg-brand-50 px-3.5 py-1.5 text-sm font-semibold text-brand-700 ring-1 ring-inset ring-brand-200 dark:bg-brand-900/30 dark:text-brand-300 dark:ring-brand-900">
              <span className="h-2 w-2 animate-pulse rounded-full bg-brand-500" />
              Real-time emergency blood network
            </span>
          </motion.div>
          <motion.h1 variants={fadeUp} className="mt-5 text-4xl font-extrabold leading-[1.08] tracking-tight text-slate-900 sm:text-5xl lg:text-6xl dark:text-white">
            Find compatible blood in <span className="text-gradient">minutes</span>, not hours.
          </motion.h1>
          <motion.p variants={fadeUp} className="mt-5 max-w-xl text-lg leading-relaxed text-slate-600 dark:text-slate-300">
            A real-time network connecting patients, donors, hospitals and blood banks so
            emergency requests are matched and fulfilled fast — when every second counts.
          </motion.p>
          <motion.div variants={fadeUp} className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Link to="/register">
              <Button size="lg" fullWidth leftIcon={<Siren className="h-5 w-5" />} className="animate-pulse-ring">
                Emergency blood request
              </Button>
            </Link>
            <Link to="/register">
              <Button size="lg" variant="secondary" fullWidth leftIcon={<HeartHandshake className="h-5 w-5" />}>
                Become a donor
              </Button>
            </Link>
          </motion.div>
          <motion.div variants={fadeUp} className="mt-8 flex items-center gap-6 text-sm text-slate-500">
            <span className="inline-flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-success" /> Verified profiles</span>
            <span className="inline-flex items-center gap-2"><Users className="h-4 w-4 text-ocean-600" /> 12k+ donors</span>
          </motion.div>
        </motion.div>

        {/* Animated availability card */}
        <motion.div initial={{ opacity: 0, scale: 0.95, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }} className="relative">
          <motion.div animate={{ y: [0, -12, 0] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }} className="glass-strong rounded-4xl p-6 shadow-soft">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="grid h-12 w-12 place-items-center rounded-2xl bg-grad-brand text-white shadow-emergency">
                  <Droplet className="h-6 w-6 fill-white/25" />
                </span>
                <div>
                  <p className="text-sm font-semibold text-slate-900 dark:text-white">Live blood availability</p>
                  <p className="text-xs text-slate-500">Updated moments ago</p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-success ring-1 ring-inset ring-emerald-200 dark:bg-emerald-900/30 dark:ring-emerald-900">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-success" /> Live
              </span>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-3">
              {BLOOD_GROUPS.slice(0, 4).map((b) => {
                const lv = LEVEL[b.level];
                return (
                  <div key={b.group} className="rounded-2xl border border-slate-100 bg-white/70 p-3.5 dark:border-slate-800 dark:bg-slate-900/60">
                    <div className="flex items-center justify-between">
                      <span className="text-lg font-bold text-brand-600">{b.group}</span>
                      <span className={`text-[11px] font-semibold ${lv.cls}`}>{lv.label}</span>
                    </div>
                    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                      <div className={`h-full rounded-full ${lv.bar} ${lv.w}`} />
                    </div>
                  </div>
                );
              })}
            </div>
            <Link to="/register" className="mt-5 flex items-center justify-center gap-2 rounded-2xl bg-slate-900 py-3 text-sm font-semibold text-white transition hover:bg-slate-800 dark:bg-white dark:text-slate-900">
              Check full availability <ArrowRight className="h-4 w-4" />
            </Link>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}

export default function LandingPage() {
  const [openFaq, setOpenFaq] = useState(0);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <Nav />
      <Hero />

      {/* Trust stats */}
      <section className="mx-auto max-w-7xl px-5 pb-8 lg:px-8">
        <Reveal className="grid grid-cols-2 gap-4 rounded-3xl border border-slate-100 bg-white p-6 shadow-card md:grid-cols-4 dark:border-slate-800 dark:bg-slate-900">
          {STATS.map((s) => (
            <div key={s.label} className="text-center">
              <div className="text-2xl font-extrabold text-slate-900 sm:text-3xl dark:text-white">{s.value}</div>
              <div className="mt-1 text-sm text-slate-500">{s.label}</div>
            </div>
          ))}
        </Reveal>
      </section>

      {/* How it works */}
      <section id="how" className="mx-auto max-w-7xl px-5 py-20 lg:px-8">
        <Reveal className="mx-auto max-w-2xl text-center">
          <span className="text-sm font-bold uppercase tracking-widest text-brand-600">How it works</span>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl dark:text-white">Three steps to save a life</h2>
          <p className="mt-3 text-slate-600 dark:text-slate-300">From emergency to fulfilment — the whole flow is built for speed.</p>
        </Reveal>
        <div className="mt-14 grid gap-6 md:grid-cols-3">
          {STEPS.map((s, i) => (
            <Reveal key={s.title} delay={i * 0.1}>
              <div className="group relative h-full rounded-3xl border border-slate-100 bg-white p-7 shadow-card transition hover:shadow-card-hover dark:border-slate-800 dark:bg-slate-900">
                <span className="absolute right-6 top-6 text-5xl font-black text-slate-100 dark:text-slate-800">{i + 1}</span>
                <span className="grid h-14 w-14 place-items-center rounded-2xl bg-grad-brand text-white shadow-emergency transition group-hover:scale-110">
                  <s.icon className="h-7 w-7" />
                </span>
                <h3 className="mt-5 text-xl font-bold text-slate-900 dark:text-white">{s.title}</h3>
                <p className="mt-2 text-slate-600 dark:text-slate-300">{s.text}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Features */}
      <section id="features" className="bg-white py-20 dark:bg-slate-900/50">
        <div className="mx-auto max-w-7xl px-5 lg:px-8">
          <Reveal className="mx-auto max-w-2xl text-center">
            <span className="text-sm font-bold uppercase tracking-widest text-ocean-600">Features</span>
            <h2 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl dark:text-white">Everything an emergency network needs</h2>
          </Reveal>
          <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f, i) => (
              <Reveal key={f.title} delay={(i % 3) * 0.08}>
                <div className="group h-full rounded-3xl border border-slate-100 bg-slate-50/60 p-6 transition hover:-translate-y-1 hover:border-brand-200 hover:bg-white hover:shadow-card-hover dark:border-slate-800 dark:bg-slate-950/40 dark:hover:border-brand-900">
                  <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600 transition group-hover:bg-grad-brand group-hover:text-white dark:bg-brand-900/30 dark:text-brand-300">
                    <f.icon className="h-6 w-6" />
                  </span>
                  <h3 className="mt-4 text-lg font-bold text-slate-900 dark:text-white">{f.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{f.text}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Live availability */}
      <section id="availability" className="mx-auto max-w-7xl px-5 py-20 lg:px-8">
        <Reveal className="mx-auto max-w-2xl text-center">
          <span className="text-sm font-bold uppercase tracking-widest text-brand-600">Live availability</span>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl dark:text-white">Blood availability at a glance</h2>
          <p className="mt-3 text-slate-600 dark:text-slate-300">A snapshot across the network. Sign in to see availability near you.</p>
        </Reveal>
        <div className="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {BLOOD_GROUPS.map((b, i) => {
            const lv = LEVEL[b.level];
            return (
              <Reveal key={b.group} delay={(i % 4) * 0.06}>
                <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-card dark:border-slate-800 dark:bg-slate-900">
                  <div className="flex items-center justify-between">
                    <span className="grid h-11 w-11 place-items-center rounded-2xl bg-brand-50 text-lg font-extrabold text-brand-600 dark:bg-brand-900/30">{b.group}</span>
                    <Droplet className={`h-5 w-5 ${lv.cls}`} />
                  </div>
                  <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                    <div className={`h-full rounded-full ${lv.bar} ${lv.w}`} />
                  </div>
                  <p className={`mt-2 text-xs font-semibold ${lv.cls}`}>{lv.label}</p>
                </div>
              </Reveal>
            );
          })}
        </div>
      </section>

      {/* Testimonials */}
      <section className="bg-white py-20 dark:bg-slate-900/50">
        <div className="mx-auto max-w-7xl px-5 lg:px-8">
          <Reveal className="mx-auto max-w-2xl text-center">
            <span className="text-sm font-bold uppercase tracking-widest text-ocean-600">Trusted by many</span>
            <h2 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl dark:text-white">Stories from the network</h2>
          </Reveal>
          <div className="mt-14 grid gap-6 md:grid-cols-3">
            {TESTIMONIALS.map((t, i) => (
              <Reveal key={t.name} delay={i * 0.1}>
                <figure className="h-full rounded-3xl border border-slate-100 bg-slate-50/60 p-7 dark:border-slate-800 dark:bg-slate-950/40">
                  <div className="flex gap-1 text-amber-400">
                    {Array.from({ length: 5 }).map((_, k) => <Star key={k} className="h-4 w-4 fill-current" />)}
                  </div>
                  <blockquote className="mt-4 text-slate-700 dark:text-slate-200">“{t.text}”</blockquote>
                  <figcaption className="mt-5 flex items-center gap-3">
                    <span className="grid h-11 w-11 place-items-center rounded-full bg-grad-ocean font-bold text-white">{t.name.charAt(0)}</span>
                    <div>
                      <div className="font-semibold text-slate-900 dark:text-white">{t.name}</div>
                      <div className="text-sm text-slate-500">{t.role}</div>
                    </div>
                  </figcaption>
                </figure>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section id="faq" className="mx-auto max-w-3xl px-5 py-20 lg:px-8">
        <Reveal className="text-center">
          <span className="text-sm font-bold uppercase tracking-widest text-brand-600">FAQ</span>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl dark:text-white">Questions, answered</h2>
        </Reveal>
        <div className="mt-10 space-y-3">
          {FAQS.map((f, i) => {
            const open = openFaq === i;
            return (
              <Reveal key={f.q} delay={i * 0.05}>
                <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
                  <button onClick={() => setOpenFaq(open ? -1 : i)} className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left">
                    <span className="font-semibold text-slate-900 dark:text-white">{f.q}</span>
                    <ChevronDown className={`h-5 w-5 shrink-0 text-slate-400 transition-transform duration-300 ${open ? 'rotate-180' : ''}`} />
                  </button>
                  <div className={`grid transition-all duration-300 ${open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'}`}>
                    <div className="overflow-hidden">
                      <p className="px-5 pb-5 text-slate-600 dark:text-slate-300">{f.a}</p>
                    </div>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </div>
      </section>

      {/* CTA */}
      <section className="mx-auto max-w-7xl px-5 pb-20 lg:px-8">
        <Reveal>
          <div className="relative overflow-hidden rounded-4xl bg-grad-brand px-8 py-14 text-center shadow-emergency sm:px-16">
            <div className="pointer-events-none absolute -right-16 -top-16 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
            <div className="pointer-events-none absolute -bottom-20 -left-10 h-64 w-64 rounded-full bg-black/10 blur-2xl" />
            <h2 className="relative text-3xl font-extrabold tracking-tight text-white sm:text-4xl">Be someone's minute that matters.</h2>
            <p className="relative mx-auto mt-3 max-w-xl text-white/90">Join thousands of donors, patients and hospitals making emergencies survivable.</p>
            <div className="relative mt-8 flex flex-col justify-center gap-3 sm:flex-row">
              <Link to="/register"><Button size="lg" variant="secondary" fullWidth className="!bg-white !text-brand-700 hover:!bg-white/90">Create free account</Button></Link>
              <Link to="/login"><Button size="lg" fullWidth className="!bg-slate-900/30 !shadow-none ring-1 ring-inset ring-white/40 hover:!bg-slate-900/40">Sign in</Button></Link>
            </div>
          </div>
        </Reveal>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <div className="mx-auto max-w-7xl px-5 py-12 lg:px-8">
          <div className="grid gap-8 md:grid-cols-4">
            <div className="md:col-span-2">
              <Logo />
              <p className="mt-4 max-w-sm text-sm text-slate-500">
                Connecting patients, donors, hospitals and blood banks so emergency blood
                requests are matched and fulfilled — fast.
              </p>
              <div className="mt-4 flex gap-3 text-sm text-slate-500">
                <span className="inline-flex items-center gap-2"><Phone className="h-4 w-4" /> 1800-BLOOD</span>
                <span className="inline-flex items-center gap-2"><Mail className="h-4 w-4" /> help@sben.org</span>
              </div>
            </div>
            <div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-white">Product</h4>
              <ul className="mt-3 space-y-2 text-sm text-slate-500">
                <li><a href="#how" className="hover:text-brand-600">How it works</a></li>
                <li><a href="#features" className="hover:text-brand-600">Features</a></li>
                <li><a href="#availability" className="hover:text-brand-600">Availability</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-white">Get started</h4>
              <ul className="mt-3 space-y-2 text-sm text-slate-500">
                <li><Link to="/register" className="hover:text-brand-600">Create account</Link></li>
                <li><Link to="/login" className="hover:text-brand-600">Sign in</Link></li>
                <li><a href="#faq" className="hover:text-brand-600">FAQ</a></li>
              </ul>
            </div>
          </div>
          <div className="mt-10 flex flex-col items-center justify-between gap-3 border-t border-slate-200 pt-6 text-sm text-slate-400 sm:flex-row dark:border-slate-800">
            <span>© 2026 Smart Blood Emergency Network</span>
            <span>Built to save lives · MCA Project</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
