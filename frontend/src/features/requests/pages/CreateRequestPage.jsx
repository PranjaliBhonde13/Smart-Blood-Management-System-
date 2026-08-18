import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Siren, Droplet, MapPin, Hash, Navigation } from 'lucide-react';
import { useCreateRequestMutation } from '../requestsApi';
import { useGeolocation } from '../../../hooks/useGeolocation';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import TextField from '../../../components/ui/TextField';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import Alert from '../../../components/ui/Alert';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
const URGENCIES = [
  { value: 'LOW', label: 'Low', hint: 'Planned / routine', color: 'slate' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Needed soon', color: 'ocean' },
  { value: 'HIGH', label: 'High', hint: 'Same-day', color: 'amber' },
  { value: 'CRITICAL', label: 'Critical', hint: 'Life-threatening', color: 'brand' },
];

const URGENCY_ACTIVE = {
  slate: 'border-slate-400 bg-slate-100 text-slate-700 dark:bg-slate-800',
  ocean: 'border-ocean-500 bg-ocean-50 text-ocean-700 dark:bg-ocean-900/20',
  amber: 'border-amber-500 bg-amber-50 text-amber-700 dark:bg-amber-900/20',
  brand: 'border-brand-500 bg-brand-50 text-brand-700 shadow-glow dark:bg-brand-900/20',
};

/**
 * Patient form to raise a blood request. Captures group, units, urgency, and
 * (optionally) the patient's location so the request can be geo-matched later.
 */
export default function CreateRequestPage() {
  const navigate = useNavigate();
  const [createRequest, { isLoading }] = useCreateRequestMutation();
  const geo = useGeolocation();
  const [form, setForm] = useState({
    bloodGroup: '', unitsRequired: 1, urgency: 'HIGH', latitude: '', longitude: '',
  });
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const useMyLocation = async () => {
    try {
      const { latitude, longitude } = await geo.request();
      setForm((f) => ({ ...f, latitude, longitude }));
      notify.success('Location captured');
    } catch { /* surfaced via geo.error */ }
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    setErr({ message: '', fieldErrors: {} });
    const body = {
      bloodGroup: form.bloodGroup,
      unitsRequired: Number(form.unitsRequired),
      urgency: form.urgency,
      ...(form.latitude !== '' && { latitude: Number(form.latitude) }),
      ...(form.longitude !== '' && { longitude: Number(form.longitude) }),
    };
    try {
      await createRequest(body).unwrap();
      notify.success('Request submitted — matching donors now');
      navigate('/patient/requests');
    } catch (error) {
      setErr(parseApiError(error));
    }
  };

  const hasLocation = form.latitude !== '' && form.longitude !== '';

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader icon={Siren} breadcrumb="Home / Requests / New" title="New blood request"
                  subtitle="Tell us what you need — we'll start matching immediately." />

      {/* Emergency banner */}
      <div className="mb-6 flex items-center gap-3 overflow-hidden rounded-2xl bg-grad-brand p-4 text-white shadow-emergency">
        <span className="grid h-11 w-11 shrink-0 animate-pulse-ring place-items-center rounded-xl bg-white/20">
          <Siren className="h-6 w-6" />
        </span>
        <div>
          <p className="font-bold">Life-threatening emergency? Call your local emergency number first.</p>
          <p className="text-sm text-white/85">This request notifies compatible donors and banks in real time.</p>
        </div>
      </div>

      <Alert type="error">{err.message}</Alert>
      {geo.error && <Alert type="error">{geo.error}</Alert>}

      <Card>
        <form className="space-y-6" onSubmit={onSubmit}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Select label="Blood group *" icon={Droplet} value={form.bloodGroup} onChange={update('bloodGroup')}
                    error={err.fieldErrors.bloodGroup} required>
              <option value="">Select…</option>
              {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
            </Select>

            <div>
              <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Units required *</span>
              <div className="relative">
                <Hash className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
                <input type="number" min={1} max={20} value={form.unitsRequired} onChange={update('unitsRequired')} required
                       className="w-full rounded-xl border border-slate-200 bg-white/80 py-2.5 pl-11 pr-3 text-slate-800 outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-100" />
              </div>
              {err.fieldErrors.unitsRequired && <span className="mt-1.5 block text-xs font-medium text-brand-600">{err.fieldErrors.unitsRequired}</span>}
            </div>
          </div>

          {/* Priority selector */}
          <div>
            <span className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-200">Urgency *</span>
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
              {URGENCIES.map((u) => {
                const active = form.urgency === u.value;
                return (
                  <button key={u.value} type="button" onClick={() => setForm({ ...form, urgency: u.value })}
                          className={'rounded-2xl border p-3 text-left transition ' +
                            (active ? URGENCY_ACTIVE[u.color] : 'border-slate-200 bg-white/60 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900/60')}>
                    <div className="text-sm font-bold">{u.label}</div>
                    <div className="mt-0.5 text-[11px] opacity-75">{u.hint}</div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Location */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4 dark:border-slate-700 dark:bg-slate-900/40">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-sm font-medium text-slate-700 dark:text-slate-200">
                <MapPin className="h-4 w-4 text-brand-600" /> Your location
                {hasLocation && <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-success dark:bg-emerald-900/30">Captured</span>}
              </div>
              <Button type="button" variant="secondary" size="sm" loading={geo.loading} onClick={useMyLocation}
                      leftIcon={<Navigation className="h-4 w-4" />}>
                Use current location
              </Button>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <TextField label="Latitude" type="number" value={form.latitude} onChange={update('latitude')} />
              <TextField label="Longitude" type="number" value={form.longitude} onChange={update('longitude')} />
            </div>
          </div>

          <div className="flex gap-3">
            <Button type="submit" size="lg" loading={isLoading} leftIcon={<Siren className="h-4 w-4" />}>Submit request</Button>
            <Button type="button" variant="ghost" onClick={() => navigate('/patient/requests')}>Cancel</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
