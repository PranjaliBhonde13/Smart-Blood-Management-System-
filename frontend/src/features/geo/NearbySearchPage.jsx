import { useState } from 'react';
import {
  MapPin, Navigation, Search, Droplet, Building2, ShieldCheck, Phone,
  MessageSquare, HeartHandshake, SlidersHorizontal,
} from 'lucide-react';
import { useLazyNearbyDonorsQuery, useLazyNearbyBanksQuery } from './geoApi';
import { useGeolocation } from '../../hooks/useGeolocation';
import { notify } from '../../lib/toast';
import MapView from './MapView';
import PageHeader from '../../components/layout/PageHeader';
import Card from '../../components/ui/Card';
import Button from '../../components/ui/Button';
import Select from '../../components/ui/Select';
import Alert from '../../components/ui/Alert';
import EmptyState from '../../components/ui/EmptyState';
import { SkeletonCard } from '../../components/ui/Skeleton';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

/**
 * Nearby-match search for patients/hospitals: pick a blood group + radius, share
 * location, and see compatible donors and stocked blood banks ranked by distance
 * — on a live map (if a Maps key is set) and as premium cards.
 */
export default function NearbySearchPage() {
  const geo = useGeolocation();
  const [triggerDonors, donorsRes] = useLazyNearbyDonorsQuery();
  const [triggerBanks, banksRes] = useLazyNearbyBanksQuery();
  const [form, setForm] = useState({ bloodGroup: 'O-', radiusKm: 15, lat: '', lng: '' });
  const [center, setCenter] = useState(null);
  const [err, setErr] = useState('');

  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const useMyLocation = async () => {
    try {
      const { latitude, longitude } = await geo.request();
      setForm((f) => ({ ...f, lat: latitude, lng: longitude }));
      notify.success('Location captured');
    } catch { /* geo.error surfaces it */ }
  };

  const onSearch = (e) => {
    e.preventDefault();
    setErr('');
    if (form.lat === '' || form.lng === '') {
      setErr('Set a location first — use "Use my location" or enter coordinates.');
      return;
    }
    const args = { bloodGroup: form.bloodGroup, lat: Number(form.lat), lng: Number(form.lng), radiusKm: Number(form.radiusKm) };
    setCenter({ lat: args.lat, lng: args.lng });
    triggerDonors(args);
    triggerBanks(args);
  };

  const donors = donorsRes.data?.data || [];
  const banks = banksRes.data?.data || [];
  const searched = donorsRes.isSuccess || banksRes.isSuccess;
  const fetching = donorsRes.isFetching || banksRes.isFetching;

  const markers = [
    ...donors.map((d) => ({ lat: Number(d.latitude), lng: Number(d.longitude), label: `Donor ${d.bloodGroup}`, color: '#dc2626' })),
    ...banks.map((b) => ({ lat: Number(b.latitude), lng: Number(b.longitude), label: b.bankName, color: '#2563eb' })),
  ];

  return (
    <div>
      <PageHeader icon={MapPin} breadcrumb="Home / Find Blood" title="Find blood nearby"
                  subtitle="Search compatible donors and stocked blood banks around a location." />

      {/* Search bar */}
      <Card className="mb-6">
        <form className="grid grid-cols-1 items-end gap-4 sm:grid-cols-2 lg:grid-cols-4" onSubmit={onSearch}>
          <Select label="Blood group" icon={Droplet} options={BLOOD_GROUPS} value={form.bloodGroup} onChange={update('bloodGroup')} />
          <div>
            <span className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Radius (km)</span>
            <div className="relative">
              <SlidersHorizontal className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
              <input type="number" min={1} max={100} value={form.radiusKm} onChange={update('radiusKm')}
                     className="w-full rounded-xl border border-slate-200 bg-white/80 py-2.5 pl-11 pr-3 text-slate-800 outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-100" />
            </div>
          </div>
          <Button type="button" variant="secondary" loading={geo.loading} onClick={useMyLocation} leftIcon={<Navigation className="h-4 w-4" />}>Use my location</Button>
          <Button type="submit" loading={fetching} leftIcon={<Search className="h-4 w-4" />}>Search</Button>
        </form>
        {form.lat !== '' && (
          <p className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500 dark:bg-slate-800">
            <MapPin className="h-3.5 w-3.5" /> Center: {Number(form.lat).toFixed(4)}, {Number(form.lng).toFixed(4)}
          </p>
        )}
        {err && <div className="mt-3"><Alert type="error">{err}</Alert></div>}
        {geo.error && <div className="mt-3"><Alert type="error">{geo.error}</Alert></div>}
      </Card>

      {center && <div className="mb-6 overflow-hidden rounded-2xl shadow-card"><MapView center={center} markers={markers} /></div>}

      {fetching && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[...Array(3)].map((_, i) => <SkeletonCard key={i} className="h-40" />)}
        </div>
      )}

      {searched && !fetching && (
        <div className="space-y-8">
          {/* Donors */}
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900 dark:text-white">
              <HeartHandshake className="h-5 w-5 text-brand-600" /> Compatible donors
              <span className="rounded-full bg-brand-50 px-2 py-0.5 text-sm font-semibold text-brand-600 dark:bg-brand-900/30">{donors.length}</span>
            </h2>
            {donors.length === 0 ? (
              <EmptyState icon={Search} title="No donors found" message="Try a larger radius." />
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {donors.map((d) => (
                  <Card key={d.donorId} hover className="flex flex-col gap-4">
                    <div className="flex items-center gap-3">
                      <span className="grid h-12 w-12 place-items-center rounded-2xl bg-grad-brand text-lg font-extrabold text-white shadow-emergency">{d.bloodGroup}</span>
                      <div className="min-w-0">
                        <div className="truncate font-semibold text-slate-900 dark:text-white">{d.city || 'Unknown city'}</div>
                        <div className="flex items-center gap-1 text-xs text-slate-500"><MapPin className="h-3.5 w-3.5" /> {d.distanceKm} km away</div>
                      </div>
                    </div>
                    <div className="mt-auto grid grid-cols-2 gap-2">
                      <Button size="sm" variant="secondary" leftIcon={<Phone className="h-4 w-4" />} onClick={() => notify.info('Contact sharing is coming soon')}>Call</Button>
                      <Button size="sm" leftIcon={<HeartHandshake className="h-4 w-4" />} onClick={() => notify.info('Request sent to donor (demo)')}>Request</Button>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </section>

          {/* Banks */}
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900 dark:text-white">
              <Building2 className="h-5 w-5 text-ocean-600" /> Blood banks
              <span className="rounded-full bg-ocean-50 px-2 py-0.5 text-sm font-semibold text-ocean-600 dark:bg-ocean-900/30">{banks.length}</span>
            </h2>
            {banks.length === 0 ? (
              <EmptyState icon={Building2} title="No stocked banks found" message="Try a larger radius." />
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {banks.map((b) => (
                  <Card key={b.bloodBankId} hover className="flex flex-col gap-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-3">
                        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-grad-ocean text-white shadow-glow-blue"><Building2 className="h-6 w-6" /></span>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1 font-semibold text-slate-900 dark:text-white">
                            <span className="truncate">{b.bankName}</span>
                            {b.verified && <ShieldCheck className="h-4 w-4 shrink-0 text-success" title="Verified" />}
                          </div>
                          <div className="flex items-center gap-1 text-xs text-slate-500"><MapPin className="h-3.5 w-3.5" /> {b.city || '—'} · {b.distanceKm} km</div>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-slate-800/50">
                      <span className="text-slate-500">Compatible units</span>
                      <span className="font-bold text-ocean-600">{b.compatibleUnits}</span>
                    </div>
                    <div className="mt-auto grid grid-cols-2 gap-2">
                      <Button size="sm" variant="secondary" leftIcon={<Phone className="h-4 w-4" />} disabled={!b.phone} onClick={() => b.phone && notify.info(b.phone)}>Call</Button>
                      <Button size="sm" variant="ocean" leftIcon={<MessageSquare className="h-4 w-4" />} onClick={() => notify.info('Message sent (demo)')}>Contact</Button>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
