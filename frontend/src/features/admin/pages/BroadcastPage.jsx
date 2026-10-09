import { useEffect, useState } from 'react';
import { Megaphone, Users, Send } from 'lucide-react';
import { useBroadcastMutation } from '../adminApi';
import { parseApiError } from '../../../lib/apiError';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import Modal from '../../../components/ui/Modal';
import Alert from '../../../components/ui/Alert';

const AUDIENCES = [
  { value: 'DONOR', label: 'Donors' },
  { value: 'BLOOD_BANK', label: 'Blood banks' },
  { value: 'HOSPITAL', label: 'Hospitals' },
  { value: 'PATIENT', label: 'Patients' },
  { value: 'ALL', label: 'Everyone (all roles)' },
];
const GROUPS = ['', 'O-', 'O+', 'A-', 'A+', 'B-', 'B+', 'AB-', 'AB+'];

const TEMPLATES = [
  { label: 'Urgent donor appeal', audience: 'DONOR', title: 'Urgent: {group} blood needed', message: 'Hospitals near you urgently need {group} blood. If you are able to donate, please open Open Requests in the app or visit your nearest blood bank today. Thank you.' },
  { label: 'Stock check', audience: 'BLOOD_BANK', title: 'Please update your stock levels', message: 'To keep matching accurate, please review and update your inventory (units and expiry dates) today.' },
];

/**
 * Targeted announcements: choose who receives it, preview the exact audience
 * size, then send. Each recipient gets an in-app notification and an email.
 */
export default function BroadcastPage() {
  const [broadcast, { isLoading }] = useBroadcastMutation();
  const [form, setForm] = useState({ audience: 'DONOR', bloodGroup: '', city: '', onlyAvailable: true, title: '', message: '' });
  const [preview, setPreview] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [err, setErr] = useState({ message: '', fieldErrors: {} });

  const update = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const supportsGroup = form.audience === 'DONOR' || form.audience === 'PATIENT';

  // Audience changed -> the old count is stale.
  useEffect(() => { setPreview(null); }, [form.audience, form.bloodGroup, form.city, form.onlyAvailable]);

  const body = (dryRun) => ({
    audience: form.audience,
    bloodGroup: supportsGroup && form.bloodGroup ? form.bloodGroup : null,
    city: form.audience !== 'ALL' && form.city.trim() ? form.city.trim() : null,
    onlyAvailable: form.audience === 'DONOR' ? form.onlyAvailable : null,
    title: form.title,
    message: form.message,
    dryRun,
  });

  const runPreview = async () => {
    setErr({ message: '', fieldErrors: {} });
    try {
      const res = await broadcast({ ...body(true), title: form.title || 'preview', message: form.message || 'preview' }).unwrap();
      setPreview(res.data.recipients);
    } catch (e) {
      setErr(parseApiError(e));
    }
  };

  const send = async () => {
    setErr({ message: '', fieldErrors: {} });
    try {
      const res = await broadcast(body(false)).unwrap();
      toast.success(`Sent to ${res.data.recipients} recipient(s)`);
      setConfirmOpen(false);
      setForm((f) => ({ ...f, title: '', message: '' }));
      setPreview(null);
    } catch (e) {
      setConfirmOpen(false);
      setErr(parseApiError(e));
    }
  };

  const applyTemplate = (t) => {
    const g = form.bloodGroup || 'O-';
    setForm((f) => ({ ...f, audience: t.audience, title: t.title.replaceAll('{group}', g), message: t.message.replaceAll('{group}', g) }));
  };

  const canSend = form.title.trim() && form.message.trim();

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon={Megaphone} breadcrumb="Home / Broadcast" title="Broadcast alert"
                  subtitle="Send an announcement to a chosen group. Recipients get an in-app notification and an email." />

      <Alert type="error">{err.message}</Alert>

      <Card className="mb-4">
        <h2 className="mb-3 text-base font-semibold">1 · Who should receive it</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Select label="Audience" value={form.audience} onChange={update('audience')} options={AUDIENCES} />
          <Select label="Blood group" value={form.bloodGroup} onChange={update('bloodGroup')} disabled={!supportsGroup}
                  options={GROUPS.map((g) => ({ value: g, label: g || 'Any group' }))}
                  hint={supportsGroup ? undefined : 'Donors and patients only'} />
          <TextField label="City" value={form.city} onChange={update('city')} placeholder="Any city" disabled={form.audience === 'ALL'} error={err.fieldErrors.city} />
        </div>
        {form.audience === 'DONOR' && (
          <label className="mt-3 flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <input id="only-available" type="checkbox" checked={form.onlyAvailable} onChange={update('onlyAvailable')} className="h-4 w-4 rounded border-slate-300 text-brand-600" />
            Only donors marked available
          </label>
        )}
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Button variant="secondary" size="sm" leftIcon={<Users className="h-4 w-4" />} loading={isLoading && !confirmOpen} onClick={runPreview}>Preview audience</Button>
          {preview !== null && (
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {preview === 0 ? 'No one matches these filters.' : `${preview} recipient${preview === 1 ? '' : 's'} will receive this.`}
            </span>
          )}
        </div>
      </Card>

      <Card>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-base font-semibold">2 · Message</h2>
          <div className="flex flex-wrap gap-2">
            {TEMPLATES.map((t) => (
              <button key={t.label} type="button" onClick={() => applyTemplate(t)}
                      className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300">
                Use template: {t.label}
              </button>
            ))}
          </div>
        </div>
        <div className="space-y-3">
          <TextField label="Title" value={form.title} onChange={update('title')} maxLength={150} error={err.fieldErrors.title} required />
          <div>
            <label htmlFor="broadcast-message" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Message</label>
            <textarea id="broadcast-message" rows={5} maxLength={2000} value={form.message} onChange={update('message')}
                      className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
            <div className="mt-1 text-right text-xs text-slate-400">{form.message.length}/2000</div>
          </div>
        </div>
        <div className="mt-4 flex justify-end">
          <Button leftIcon={<Send className="h-4 w-4" />} disabled={!canSend} onClick={() => setConfirmOpen(true)}>Review &amp; send</Button>
        </div>
      </Card>

      <Modal open={confirmOpen} onClose={() => setConfirmOpen(false)} title="Send this broadcast?"
             footer={<>
               <Button variant="secondary" onClick={() => setConfirmOpen(false)}>Cancel</Button>
               <Button loading={isLoading} onClick={send}>Send now</Button>
             </>}>
        <dl className="space-y-2 text-sm">
          <div><dt className="text-slate-400">To</dt><dd className="font-medium">
            {AUDIENCES.find((x) => x.value === form.audience)?.label}
            {supportsGroup && form.bloodGroup ? ` · ${form.bloodGroup}` : ''}
            {form.city.trim() && form.audience !== 'ALL' ? ` · ${form.city.trim()}` : ''}
            {form.audience === 'DONOR' && form.onlyAvailable ? ' · available only' : ''}
            {preview !== null ? ` (${preview} recipients)` : ''}
          </dd></div>
          <div><dt className="text-slate-400">Title</dt><dd className="font-medium">{form.title}</dd></div>
          <div><dt className="text-slate-400">Message</dt><dd className="whitespace-pre-wrap">{form.message}</dd></div>
        </dl>
        <p className="mt-3 text-xs text-slate-400">This can't be undone. Each recipient also gets an email.</p>
      </Modal>
    </div>
  );
}
