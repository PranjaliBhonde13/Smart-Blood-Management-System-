import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { KeyRound, Lock, ShieldCheck } from 'lucide-react';
import { useChangePasswordMutation } from '../authApi';
import { tokensRefreshed } from '../authSlice';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import TextField from '../../../components/ui/TextField';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' };

/**
 * Change password for any signed-in role. The server checks the current
 * password, signs out every other session and returns fresh tokens so this
 * session stays signed in.
 */
export default function ChangePasswordPage() {
  const dispatch = useDispatch();
  const [changePassword, { isLoading }] = useChangePasswordMutation();
  const [form, setForm] = useState(EMPTY);
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  const onSubmit = async (e) => {
    e.preventDefault();
    const fieldErrors = {};
    if (!form.currentPassword) fieldErrors.currentPassword = 'Enter your current password';
    if (form.newPassword.length < 8 || form.newPassword.length > 72) fieldErrors.newPassword = 'Use 8 to 72 characters';
    else if (form.newPassword === form.currentPassword) fieldErrors.newPassword = 'New password must be different from your current password';
    if (form.confirmPassword !== form.newPassword) fieldErrors.confirmPassword = 'Passwords do not match';
    if (Object.keys(fieldErrors).length) { setErr({ message: '', fieldErrors }); return; }

    setErr({ message: '', fieldErrors: {} });
    try {
      const res = await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword }).unwrap();
      // Old tokens are now invalid on the server; keep this session on the new pair.
      dispatch(tokensRefreshed(res.data));
      setForm(EMPTY);
      notify.success('Password changed. Other devices have been signed out.');
    } catch (error) {
      const parsed = parseApiError(error);
      // Field errors are shown inline; keep the banner for anything else.
      setErr(Object.keys(parsed.fieldErrors).length ? { ...parsed, message: '' } : parsed);
    }
  };

  return (
    <div className="mx-auto max-w-xl">
      <PageHeader icon={KeyRound} breadcrumb="Home / Account / Password" title="Change password"
                  subtitle="Choose a new password for your account." />
      <Card>
        <Alert type="error">{err.message}</Alert>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <TextField id="current-password" label="Current password *" type="password" icon={Lock}
                     autoComplete="current-password" value={form.currentPassword}
                     onChange={update('currentPassword')} error={err.fieldErrors.currentPassword} required />
          <TextField id="new-password" label="New password *" type="password" icon={Lock}
                     autoComplete="new-password" value={form.newPassword} onChange={update('newPassword')}
                     error={err.fieldErrors.newPassword} hint="8 to 72 characters." maxLength={72} required />
          <TextField id="confirm-password" label="Confirm new password *" type="password" icon={Lock}
                     autoComplete="new-password" value={form.confirmPassword} onChange={update('confirmPassword')}
                     error={err.fieldErrors.confirmPassword} maxLength={72} required />
          <p className="flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2.5 text-xs text-slate-500 dark:bg-slate-800/60">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
            You'll stay signed in here. Every other browser or device signed in to your account will be signed out,
            and we'll email you to confirm the change.
          </p>
          <Button type="submit" size="lg" loading={isLoading} leftIcon={<KeyRound className="h-4 w-4" />}>Change password</Button>
        </form>
      </Card>
    </div>
  );
}
