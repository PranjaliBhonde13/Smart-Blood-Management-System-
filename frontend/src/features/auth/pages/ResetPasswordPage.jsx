import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, KeyRound, Lock } from 'lucide-react';
import { useResetPasswordMutation } from '../authApi';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import AuthLayout from '../AuthLayout';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form, setForm] = useState({
    email: params.get('email') || '', token: '', newPassword: '',
  });
  const [resetPassword, { isLoading }] = useResetPasswordMutation();
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    try {
      await resetPassword(form).unwrap();
      notify.success('Password updated — sign in with your new password');
      navigate('/login');
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <AuthLayout
      title="Set a new password"
      subtitle="Enter the code from your email and choose a new password."
      footer={<Link className="font-semibold text-brand-600" to="/login">Back to sign in</Link>}
    >
      <Alert type="error">{err.message}</Alert>
      <form className="space-y-4" onSubmit={onSubmit}>
        <TextField label="Email address" type="email" icon={Mail} value={form.email} onChange={update('email')}
                   error={err.fieldErrors.email} required />
        <TextField label="Reset code" icon={KeyRound} value={form.token} onChange={update('token')}
                   error={err.fieldErrors.token} required />
        <TextField label="New password" type="password" icon={Lock} value={form.newPassword} onChange={update('newPassword')}
                   error={err.fieldErrors.newPassword} hint="8 to 72 characters." minLength={8} maxLength={72} required />
        <Button type="submit" fullWidth size="lg" loading={isLoading}>Update password</Button>
      </form>
    </AuthLayout>
  );
}
