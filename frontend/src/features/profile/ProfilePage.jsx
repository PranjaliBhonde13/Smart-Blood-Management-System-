import { useEffect, useState } from 'react';
import { UserRound, Navigation, Save, ShieldCheck } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useGeolocation } from '../../hooks/useGeolocation';
import { PROFILE_SCHEMA } from './profileSchema';
import { useGetMyProfileQuery, useUpsertMyProfileMutation } from './profileApi';
import { parseApiError } from '../../lib/apiError';
import { notify } from '../../lib/toast';
import { ROLE_LABEL } from '../../lib/constants';
import PageHeader from '../../components/layout/PageHeader';
import Card from '../../components/ui/Card';
import TextField from '../../components/ui/TextField';
import Select from '../../components/ui/Select';
import Button from '../../components/ui/Button';
import Alert from '../../components/ui/Alert';
import Skeleton from '../../components/ui/Skeleton';

/**
 * One profile screen for every role. Renders fields from PROFILE_SCHEMA[role],
 * prefills them from the server, and upserts on save. Geo fields get a
 * "Use my location" helper. Handles loading (skeleton), first-time empty
 * profiles (404 → blank form), success, and validation errors.
 */
export default function ProfilePage() {
  const { user, role } = useAuth();
  const schema = PROFILE_SCHEMA[role];
  const resource = schema?.resource;

  const { data, isLoading, isError, error } = useGetMyProfileQuery(resource, { skip: !resource });
  const [upsert, { isLoading: saving }] = useUpsertMyProfileMutation();
  const geo = useGeolocation();

  const [form, setForm] = useState({});
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  useEffect(() => {
    if (data?.data) {
      const clean = {};
      schema.fields.forEach((f) => { clean[f.name] = data.data[f.name] ?? ''; });
      setForm(clean);
    }
  }, [data, schema]);

  if (!schema) {
    return (
      <div className="mx-auto max-w-2xl">
        <PageHeader icon={UserRound} title="My Profile" />
        <Card><p className="text-slate-500">Profiles are managed by the administrator for this role.</p></Card>
      </div>
    );
  }

  const update = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  const useMyLocation = async () => {
    try {
      const { latitude, longitude } = await geo.request();
      setForm((f) => ({ ...f, latitude, longitude }));
      notify.success('Location captured');
    } catch { /* error surfaced via geo.error */ }
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    const body = Object.fromEntries(
      Object.entries(form).filter(([, v]) => v !== '' && v !== null && v !== undefined)
    );
    try {
      await upsert({ resource, body }).unwrap();
      notify.success('Profile saved successfully');
    } catch (error2) {
      setErr(parseApiError(error2));
    }
  };

  const hasGeo = schema.fields.some((f) => f.geo);
  const loadError = isError && error?.status !== 404;

  // Profile completion — share of fields that currently have a value.
  const filled = schema.fields.filter((f) => form[f.name] !== '' && form[f.name] != null).length;
  const completion = schema.fields.length ? Math.round((filled / schema.fields.length) * 100) : 0;
  const initial = user?.fullName?.charAt(0)?.toUpperCase() || '?';

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon={UserRound} breadcrumb="Home / Profile" title="My Profile"
                  subtitle="Keep your details current so matching stays accurate." />

      {loadError && <Alert type="error">Could not load your profile. Please retry.</Alert>}
      <Alert type="error">{err.message}</Alert>
      {geo.error && <Alert type="error">{geo.error}</Alert>}

      {/* Identity + completion header */}
      <div className="mb-6 overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900">
        <div className="h-20 bg-grad-brand" />
        <div className="flex flex-wrap items-end gap-4 px-6 pb-6">
          <span className="-mt-10 grid h-20 w-20 shrink-0 place-items-center rounded-3xl bg-grad-brand text-2xl font-extrabold text-white shadow-emergency ring-4 ring-white dark:ring-slate-900">{initial}</span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h2 className="truncate text-xl font-bold text-slate-900 dark:text-white">{user?.fullName || 'Your name'}</h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-ocean-50 px-2 py-0.5 text-xs font-semibold text-ocean-600 dark:bg-ocean-900/30"><ShieldCheck className="h-3.5 w-3.5" /> {ROLE_LABEL[role]}</span>
            </div>
            <p className="truncate text-sm text-slate-500">{user?.email}</p>
          </div>
          <div className="w-full sm:w-56">
            <div className="mb-1 flex items-center justify-between text-xs font-medium text-slate-500">
              <span>Profile completion</span><span className="font-bold text-brand-600">{completion}%</span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
              <div className="h-full rounded-full bg-grad-brand transition-all duration-500" style={{ width: `${completion}%` }} />
            </div>
          </div>
        </div>
      </div>

      <Card>
        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {[...Array(6)].map((_, i) => <Skeleton key={i} className="h-12" />)}
          </div>
        ) : (
          <form className="space-y-5" onSubmit={onSubmit}>
            {hasGeo && (
              <Button type="button" variant="secondary" loading={geo.loading} onClick={useMyLocation} leftIcon={<Navigation className="h-4 w-4" />}>
                Use my current location
              </Button>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {schema.fields.map((f) => (
                <div key={f.name} className={f.type === 'text' && !f.geo ? 'sm:col-span-2' : ''}>
                  {f.type === 'select' ? (
                    <Select label={`${f.label}${f.required ? ' *' : ''}`} value={form[f.name] ?? ''}
                            onChange={update(f.name)} required={f.required} error={err.fieldErrors[f.name]}>
                      <option value="">Select…</option>
                      {f.options.map((o) => <option key={o} value={o}>{o}</option>)}
                    </Select>
                  ) : (
                    <TextField
                      label={`${f.label}${f.required ? ' *' : ''}`}
                      type={f.type}
                      value={form[f.name] ?? ''}
                      onChange={update(f.name)}
                      error={err.fieldErrors[f.name]}
                      required={f.required}
                    />
                  )}
                </div>
              ))}
            </div>

            <Button type="submit" size="lg" loading={saving} leftIcon={<Save className="h-4 w-4" />}>Save profile</Button>
          </form>
        )}
      </Card>
    </div>
  );
}
