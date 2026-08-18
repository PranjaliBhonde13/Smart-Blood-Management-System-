import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Mail, KeyRound } from 'lucide-react';
import { useForgotPasswordMutation } from '../authApi';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import AuthLayout from '../AuthLayout';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [forgotPassword, { isLoading }] = useForgotPasswordMutation();
  const [err, setErr] = useState({ message: '' });
  const [info, setInfo] = useState('');

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '' });
    try {
      await forgotPassword({ email }).unwrap();
      const msg = 'If that email exists, a reset code has been sent.';
      setInfo(msg);
      notify.success(msg);
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We'll email you a code to reset it."
      footer={<Link className="font-semibold text-brand-600" to="/login">Back to sign in</Link>}
    >
      <Alert type="error">{err.message}</Alert>
      <Alert type="success">{info}</Alert>
      <form className="space-y-4" onSubmit={onSubmit}>
        <TextField label="Email address" type="email" icon={Mail} value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Button type="submit" fullWidth size="lg" loading={isLoading}>Send reset code</Button>
        <Button type="button" variant="secondary" fullWidth leftIcon={<KeyRound className="h-4 w-4" />}
                onClick={() => navigate(`/reset-password?email=${encodeURIComponent(email)}`)}>
          I already have a code
        </Button>
      </form>
    </AuthLayout>
  );
}
