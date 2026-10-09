import { useEffect, useState } from 'react';
import { UserRound, Navigation, Save } from 'lucide-react';
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
import ProfileSummaryCard from './ProfileSummaryCard';
import DonorProfilePage from './donor/DonorProfilePage';
import PatientProfilePage from './patient/PatientProfilePage';
import HospitalProfilePage from './hospital/HospitalProfilePage';

/** Patients, donors and hospitals have dedicated, sectioned profiles; other roles use the schema-driven form. */
export default function ProfilePage() {
  const { role } = useAuth();
  if (role === 'PATIENT') return <PatientProfilePage />;
  if (role === 'HOSPITAL') return <HospitalProfilePage />;
  return role === 'DONOR' ? <DonorProfilePage /> : <SchemaProfilePage />;
}

/**
 * Generic profile screen. Renders fields from PROFILE_SCHEMA[role],
 * prefills them from the server, and upserts on save. Geo fields get a
 * "Use my location" helper. Handles loading (skeleton), first-time empty
 * profiles (404 → blank form), success, and validation errors.
 */
/** Map a stored value onto a select option case-insensitively (e.g. 'Male' -> 'MALE'). */
function normaliseOption(field, value) {
  if (field.type !== 'select' || value === '') return value;
  const match = field.options
    .map((o) => (typeof o === 'string' ? o : o.value))
    .find((v) => String(v).toLowerCase() === String(value).toLowerCase());
  return match ?? value;
}

function SchemaProfilePage() {
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
      schema.fields.forEach((f) => { clean[f.name] = normaliseOption(f, data.data[f.name] ?? ''); });
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

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon={UserRound} breadcrumb="Home / Profile" title="My Profile"
                  subtitle="Keep your details current so matching stays accurate." />

      {loadError && <Alert type="error">Could not load your profile. Please retry.</Alert>}
      {role === 'BLOOD_BANK' && data?.data && (
        data.data.verified ? (
          <Alert type="success">Verified by an administrator — you're active on the network.</Alert>
        ) : data.data.rejectionReason ? (
          <Alert type="error" title="Verification not approved">
            {data.data.rejectionReason}. Correct your details below and save to resubmit for review.
          </Alert>
        ) : (
          <Alert type="info">Awaiting administrator verification. You'll get a notification once it's reviewed.</Alert>
        )
      )}
      <Alert type="error">{err.message}</Alert>
      {geo.error && <Alert type="error">{geo.error}</Alert>}

      <ProfileSummaryCard name={user?.fullName} email={user?.email} roleLabel={ROLE_LABEL[role]} completion={completion} />

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
                      {f.options.map((o) => {
                        const value = typeof o === 'string' ? o : o.value;
                        return <option key={value} value={value}>{typeof o === 'string' ? o : o.label}</option>;
                      })}
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
