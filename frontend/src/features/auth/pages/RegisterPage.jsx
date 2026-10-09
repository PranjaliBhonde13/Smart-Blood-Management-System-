import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  User, Mail, Phone, Lock, Check, ArrowRight, ArrowLeft,
  HeartHandshake, Hospital, Building2, HeartPulse,
} from 'lucide-react';
import { useRegisterMutation } from '../authApi';
import { parseApiError } from '../../../lib/apiError';
import { ROLES } from '../../../lib/constants';
import { notify } from '../../../lib/toast';
import AuthLayout from '../AuthLayout';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

/**
 * Multi-step registration wizard. Purely a UI/UX enhancement — it collects and
 * submits exactly the fields the backend /auth/register accepts
 * ({ fullName, email, phone, password, role }); no schema or API change.
 *
 * Flow: role → identity → credentials → review → submit → OTP verification.
 */
const ROLE_OPTIONS = [
  { value: ROLES.PATIENT, label: 'Patient', desc: 'I need blood', icon: HeartPulse },
  { value: ROLES.DONOR, label: 'Donor', desc: 'I want to donate', icon: HeartHandshake },
  { value: ROLES.HOSPITAL, label: 'Hospital', desc: 'Manage patient requests', icon: Hospital },
  { value: ROLES.BLOOD_BANK, label: 'Blood Bank', desc: 'Manage blood inventory', icon: Building2 },
];

const STEPS = ['Account type', 'Your details', 'Credentials', 'Review'];

const stepVariants = {
  enter: (dir) => ({ opacity: 0, x: dir > 0 ? 40 : -40 }),
  center: { opacity: 1, x: 0 },
  exit: (dir) => ({ opacity: 0, x: dir > 0 ? -40 : 40 }),
};

export default function RegisterPage() {
  const navigate = useNavigate();
  const [register, { isLoading }] = useRegisterMutation();
  const [step, setStep] = useState(0);
  const [dir, setDir] = useState(1);
  const [form, setForm] = useState({
    fullName: '', email: '', phone: '', password: '', role: ROLES.PATIENT,
  });
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const go = (next) => { setDir(next > step ? 1 : -1); setStep(next); };

  /** Lightweight per-step gating (does not replace server validation). */
  const canAdvance = () => {
    if (step === 1) return form.fullName.trim().length > 1;
    if (step === 2) return /\S+@\S+\.\S+/.test(form.email) && form.password.length >= 8;
    return true;
  };

  const next = () => { if (canAdvance()) go(step + 1); };
  const back = () => go(Math.max(0, step - 1));

  const onSubmit = async () => {
    setErr({ message: '', fieldErrors: {} });
    try {
      await register(form).unwrap();
      notify.success('Account created — verify your email');
      navigate(`/verify-otp?email=${encodeURIComponent(form.email)}`);
    } catch (error) {
      const parsed = parseApiError(error);
      setErr(parsed);
      // Jump back to the step that owns the errored field.
      if (parsed.fieldErrors?.fullName || parsed.fieldErrors?.phone) go(1);
      else if (parsed.fieldErrors?.email || parsed.fieldErrors?.password) go(2);
    }
  };

  const selectedRole = ROLE_OPTIONS.find((r) => r.value === form.role);

  return (
    <AuthLayout
      title="Create your account"
      subtitle={`Step ${step + 1} of ${STEPS.length} · ${STEPS[step]}`}
      footer={<>Already registered? <Link className="font-semibold text-brand-600 hover:text-brand-700" to="/login">Sign in</Link></>}
    >
      {/* Progress */}
      <div className="mb-6">
        <div className="flex items-center gap-2">
          {STEPS.map((label, i) => (
            <div key={label} className="flex flex-1 items-center gap-2">
              <div className="flex flex-col items-center gap-1.5">
                <div
                  className={
                    'grid h-8 w-8 place-items-center rounded-full text-xs font-bold transition-all ' +
                    (i < step
                      ? 'bg-success text-white'
                      : i === step
                      ? 'bg-grad-brand text-white shadow-emergency'
                      : 'bg-slate-100 text-slate-400 dark:bg-slate-800')
                  }
                >
                  {i < step ? <Check className="h-4 w-4" /> : i + 1}
                </div>
              </div>
              {i < STEPS.length - 1 && (
                <div className="h-1 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                  <div className={`h-full rounded-full bg-success transition-all duration-500 ${i < step ? 'w-full' : 'w-0'}`} />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      <Alert type="error">{err.message}</Alert>

      <div className="relative overflow-hidden">
        <AnimatePresence mode="wait" custom={dir}>
          <motion.div
            key={step}
            custom={dir}
            variants={stepVariants}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
          >
            {/* Step 0 — role */}
            {step === 0 && (
              <div className="grid grid-cols-2 gap-3">
                {ROLE_OPTIONS.map((o) => {
                  const active = form.role === o.value;
                  return (
                    <button
                      key={o.value}
                      type="button"
                      onClick={() => setForm({ ...form, role: o.value })}
                      className={
                        'group flex flex-col items-start gap-2 rounded-2xl border p-4 text-left transition ' +
                        (active
                          ? 'border-brand-500 bg-brand-50 shadow-glow dark:bg-brand-900/20'
                          : 'border-slate-200 bg-white/60 hover:border-brand-300 dark:border-slate-700 dark:bg-slate-900/60')
                      }
                    >
                      <span className={'grid h-10 w-10 place-items-center rounded-xl transition ' + (active ? 'bg-grad-brand text-white' : 'bg-slate-100 text-slate-500 dark:bg-slate-800')}>
                        <o.icon className="h-5 w-5" />
                      </span>
                      <span className="text-sm font-bold text-slate-900 dark:text-white">{o.label}</span>
                      <span className="text-xs text-slate-500">{o.desc}</span>
                    </button>
                  );
                })}
              </div>
            )}

            {/* Step 1 — identity */}
            {step === 1 && (
              <div className="space-y-4">
                <TextField label="Full name" icon={User} value={form.fullName} onChange={update('fullName')}
                           error={err.fieldErrors.fullName} autoComplete="name" required />
                <TextField label="Phone number" icon={Phone} value={form.phone} onChange={update('phone')}
                           error={err.fieldErrors.phone} autoComplete="tel" hint="Optional — helps responders reach you." />
              </div>
            )}

            {/* Step 2 — credentials */}
            {step === 2 && (
              <div className="space-y-4">
                <TextField label="Email address" type="email" icon={Mail} value={form.email} onChange={update('email')}
                           error={err.fieldErrors.email} autoComplete="email" required />
                <TextField label="Password" type="password" icon={Lock} value={form.password} onChange={update('password')}
                           error={err.fieldErrors.password} autoComplete="new-password" hint="8 to 72 characters." required />
              </div>
            )}

            {/* Step 3 — review */}
            {step === 3 && (
              <div className="space-y-3">
                <div className="rounded-2xl border border-slate-200 bg-white/60 p-4 dark:border-slate-700 dark:bg-slate-900/60">
                  <ReviewRow icon={selectedRole?.icon} label="Account type" value={selectedRole?.label} onEdit={() => go(0)} />
                  <ReviewRow icon={User} label="Full name" value={form.fullName || '—'} onEdit={() => go(1)} />
                  <ReviewRow icon={Phone} label="Phone" value={form.phone || 'Not provided'} onEdit={() => go(1)} />
                  <ReviewRow icon={Mail} label="Email" value={form.email || '—'} onEdit={() => go(2)} />
                  <ReviewRow icon={Lock} label="Password" value={'•'.repeat(Math.min(form.password.length, 10)) || '—'} onEdit={() => go(2)} last />
                </div>
                <p className="px-1 text-xs text-slate-400">
                  By creating an account you agree to our Terms of Service and Privacy Policy.
                </p>
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Nav buttons */}
      <div className="mt-7 flex gap-3">
        {step > 0 && (
          <Button variant="secondary" onClick={back} leftIcon={<ArrowLeft className="h-4 w-4" />}>Back</Button>
        )}
        {step < STEPS.length - 1 ? (
          <Button fullWidth onClick={next} disabled={!canAdvance()} rightIcon={<ArrowRight className="h-4 w-4" />}>
            Continue
          </Button>
        ) : (
          <Button fullWidth onClick={onSubmit} loading={isLoading} leftIcon={<Check className="h-4 w-4" />}>
            Create account
          </Button>
        )}
      </div>
    </AuthLayout>
  );
}

function ReviewRow({ icon: Icon, label, value, onEdit, last = false }) {
  return (
    <div className={`flex items-center justify-between gap-3 py-2.5 ${last ? '' : 'border-b border-slate-100 dark:border-slate-800'}`}>
      <div className="flex min-w-0 items-center gap-3">
        {Icon && <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-800"><Icon className="h-4 w-4" /></span>}
        <div className="min-w-0">
          <div className="text-xs text-slate-400">{label}</div>
          <div className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{value}</div>
        </div>
      </div>
      <button type="button" onClick={onEdit} className="shrink-0 text-xs font-semibold text-brand-600 hover:text-brand-700">Edit</button>
    </div>
  );
}
