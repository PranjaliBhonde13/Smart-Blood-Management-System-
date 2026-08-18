import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { Mail, Lock } from 'lucide-react';
import { useLoginMutation } from '../authApi';
import { setCredentials } from '../authSlice';
import { parseApiError } from '../../../lib/apiError';
import { ROLE_HOME } from '../../../lib/constants';
import { notify } from '../../../lib/toast';
import AuthLayout from '../AuthLayout';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const [login, { isLoading }] = useLoginMutation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [remember, setRemember] = useState(true);
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    try {
      const res = await login(form).unwrap();
      dispatch(setCredentials(res.data)); // {accessToken, refreshToken, user}
      notify.success(`Welcome back, ${res.data.user.fullName?.split(' ')[0] || ''}`);
      // Return to the page they wanted, else their role's home dashboard.
      const dest = location.state?.from?.pathname || ROLE_HOME[res.data.user.role] || '/';
      navigate(dest, { replace: true });
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to continue to your dashboard."
      footer={<>New here? <Link className="font-semibold text-brand-600 hover:text-brand-700" to="/register">Create an account</Link></>}
    >
      <Alert type="error">{err.message}</Alert>
      <form className="space-y-4" onSubmit={onSubmit}>
        <TextField label="Email address" type="email" icon={Mail} value={form.email} onChange={update('email')}
                   error={err.fieldErrors.email} autoComplete="email" required />
        <TextField label="Password" type="password" icon={Lock} value={form.password} onChange={update('password')}
                   error={err.fieldErrors.password} autoComplete="current-password" required />

        <div className="flex items-center justify-between">
          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)}
                   className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
            Remember me
          </label>
          <Link to="/forgot-password" className="text-sm font-semibold text-brand-600 hover:text-brand-700">Forgot password?</Link>
        </div>

        <Button type="submit" fullWidth size="lg" loading={isLoading}>Sign in</Button>
      </form>

      <div className="my-6 flex items-center gap-3 text-xs font-medium uppercase tracking-wide text-slate-400">
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" /> or continue with <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        {['Google', 'Apple'].map((p) => (
          <button key={p} type="button" disabled title="Coming soon"
                  className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white/70 py-2.5 text-sm font-semibold text-slate-600 opacity-70 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
            {p}
          </button>
        ))}
      </div>
    </AuthLayout>
  );
}
