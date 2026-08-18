import { useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, ShieldCheck } from 'lucide-react';
import { useVerifyOtpMutation, useResendOtpMutation } from '../authApi';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import AuthLayout from '../AuthLayout';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

/** Segmented 6-box code input. Keeps the parent `otp` string in sync. */
function OtpInput({ value, onChange }) {
  const refs = useRef([]);
  const digits = value.padEnd(6, ' ').slice(0, 6).split('');

  const setAt = (i, ch) => {
    const arr = value.padEnd(6, ' ').slice(0, 6).split('');
    arr[i] = ch || ' ';
    onChange(arr.join('').replace(/\s+$/g, '').trimEnd().replace(/\s/g, ''));
  };

  const handleChange = (i) => (e) => {
    const ch = e.target.value.replace(/\D/g, '').slice(-1);
    setAt(i, ch);
    if (ch && i < 5) refs.current[i + 1]?.focus();
  };

  const handleKey = (i) => (e) => {
    if (e.key === 'Backspace' && !digits[i].trim() && i > 0) refs.current[i - 1]?.focus();
  };

  const handlePaste = (e) => {
    const paste = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (paste) { e.preventDefault(); onChange(paste); refs.current[Math.min(paste.length, 5)]?.focus(); }
  };

  return (
    <div className="flex justify-between gap-2" onPaste={handlePaste}>
      {Array.from({ length: 6 }).map((_, i) => (
        <input
          key={i}
          ref={(el) => (refs.current[i] = el)}
          inputMode="numeric"
          maxLength={1}
          value={digits[i].trim()}
          onChange={handleChange(i)}
          onKeyDown={handleKey(i)}
          className="h-14 w-full rounded-2xl border border-slate-200 bg-white/80 text-center text-2xl font-bold text-slate-900 outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900/70 dark:text-white"
        />
      ))}
    </div>
  );
}

export default function VerifyOtpPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get('email') || '');
  const [otp, setOtp] = useState('');
  const [verifyOtp, { isLoading }] = useVerifyOtpMutation();
  const [resendOtp, { isLoading: resending }] = useResendOtpMutation();
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    try {
      await verifyOtp({ email, otp }).unwrap();
      notify.success('Email verified — you can sign in now');
      navigate('/login');
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  const onResend = async () => {
    try {
      await resendOtp({ email }).unwrap();
      notify.info('A new code has been sent (check the backend console in dev).');
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <AuthLayout
      title="Verify your email"
      subtitle="Enter the 6-digit code we sent to your inbox."
      footer={<Link className="font-semibold text-brand-600 hover:text-brand-700" to="/login">Back to sign in</Link>}
    >
      <div className="mb-6 flex justify-center">
        <span className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-900/30">
          <ShieldCheck className="h-7 w-7" />
        </span>
      </div>
      <Alert type="error">{err.message}</Alert>
      <form className="space-y-5" onSubmit={onSubmit}>
        <TextField label="Email" type="email" icon={Mail} value={email} onChange={(e) => setEmail(e.target.value)}
                   error={err.fieldErrors.email} required />
        <div>
          <span className="mb-2 block text-sm font-medium text-slate-600 dark:text-slate-300">Verification code</span>
          <OtpInput value={otp} onChange={setOtp} />
          {err.fieldErrors.otp && <span className="mt-1.5 block text-xs font-medium text-brand-600">{err.fieldErrors.otp}</span>}
        </div>
        <Button type="submit" fullWidth size="lg" loading={isLoading} disabled={otp.length < 6}>Verify email</Button>
        <div className="text-center text-sm text-slate-500">
          Didn't get it?{' '}
          <button type="button" onClick={onResend} disabled={resending}
                  className="font-semibold text-brand-600 hover:text-brand-700 disabled:opacity-60">
            {resending ? 'Sending…' : 'Resend code'}
          </button>
        </div>
      </form>
    </AuthLayout>
  );
}
