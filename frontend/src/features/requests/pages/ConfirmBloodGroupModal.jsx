import { useEffect, useState } from 'react';
import { useConfirmBloodGroupMutation } from '../requestsApi';
import { parseApiError } from '../../../lib/apiError';
import { notify } from '../../../lib/toast';
import { formatDateTime } from '../../../lib/format';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import { Badge } from '../../../components/ui/Badge';

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];
const COMPONENT_SHORT = {
  WHOLE_BLOOD: 'Whole blood', PACKED_RED_CELLS: 'PRBC', PLATELETS: 'Platelets',
  FRESH_FROZEN_PLASMA: 'FFP', CRYOPRECIPITATE: 'Cryo', NOT_SURE: 'Component not specified',
};

/** Where the requested group came from (server-set), and whether a hospital/admin confirmed it. */
export function BloodGroupSourceBadge({ r, onConfirm }) {
  if (!r.bloodGroupSource) return null; // request created before group sourcing existed
  if (r.bloodGroupSource === 'PROFILE_VERIFIED') return <div className="mt-1"><Badge tone="success">Verified profile</Badge></div>;
  if (r.bloodGroupConfirmed) return <div className="mt-1"><Badge tone="success">Confirmed</Badge></div>;
  return (
    <div className="mt-1 flex flex-col items-start gap-1">
      <Badge tone="warning">{r.bloodGroupSource === 'DECLARED' ? 'Declared' : 'Unverified'}</Badge>
      {onConfirm && (
        <button type="button" onClick={onConfirm} className="text-xs font-semibold text-ocean-600 hover:underline">Confirm / correct</button>
      )}
    </div>
  );
}

/** Recipient summary for the hospital queue. */
export function RecipientCell({ r }) {
  if (!r.requestFor) return <span className="text-xs text-slate-400">—</span>;
  return (
    <div className="min-w-[9rem] text-sm">
      <div className="font-medium text-slate-800 dark:text-slate-100">{r.recipientName || '—'}{r.recipientAge != null && <span className="text-slate-500">, {r.recipientAge}</span>}</div>
      <div className="text-xs text-slate-500">
        {r.requestFor === 'SELF' ? 'Requester (self)' : `For another patient${r.recipientRelationship ? ` · ${r.recipientRelationship}` : ''}`}
      </div>
      <div className="text-xs text-slate-500">
        {COMPONENT_SHORT[r.component] || ''}{r.requiredBy && ` · by ${formatDateTime(r.requiredBy)}`}
      </div>
      {r.locationNotes && <div className="text-xs text-slate-500">{r.locationNotes}</div>}
    </div>
  );
}

/**
 * Confirm the requested blood group as recorded, or correct it with a reason.
 * Both are written to the audit log on the server. This validates the request;
 * compatibility and crossmatching remain with blood-bank staff.
 */
export default function ConfirmBloodGroupModal({ request, onClose }) {
  const [group, setGroup] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [confirm, { isLoading }] = useConfirmBloodGroupMutation();

  useEffect(() => {
    if (request) { setGroup(request.bloodGroup); setReason(''); setError(''); }
  }, [request]);

  if (!request) return null;
  const correcting = group !== request.bloodGroup;

  const submit = async () => {
    if (correcting && !reason.trim()) { setError('Give a reason for the correction'); return; }
    try {
      await confirm({ id: request.id, bloodGroup: group, reason: reason.trim() || null }).unwrap();
      notify.success(correcting ? `Request #${request.id} corrected to ${group}` : `Blood group on #${request.id} confirmed`);
      onClose();
    } catch (e) {
      const { message, fieldErrors } = parseApiError(e);
      setError(fieldErrors.reason || message);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Blood group for request #${request.id}`}
           footer={<>
             <Button variant="secondary" onClick={onClose}>Cancel</Button>
             <Button loading={isLoading} onClick={submit}>{correcting ? 'Save correction' : `Confirm ${group}`}</Button>
           </>}>
      <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">
        Requested: <b>{request.bloodGroup}</b> ({request.bloodGroupSource === 'DECLARED' ? 'entered for another patient' : 'from an unverified profile'}).
        Confirm it against the patient's lab report or your hospital's typing, or correct it.
      </p>
      <Select id="confirm-group" label="Blood group" value={group} onChange={(e) => { setGroup(e.target.value); setError(''); }}>
        {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
      </Select>
      {correcting && (
        <div className="mt-3">
          <label htmlFor="confirm-reason" className="mb-1.5 block text-sm font-medium">Reason for correction *</label>
          <textarea id="confirm-reason" rows={3} maxLength={500} value={reason} onChange={(e) => { setReason(e.target.value); setError(''); }}
                    className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
        </div>
      )}
      {error && <p className="mt-2 text-xs font-medium text-brand-600" role="alert">{error}</p>}
      <p className="mt-3 text-xs text-slate-500">This is recorded in the audit log and the patient is told about corrections. Crossmatching and component selection remain with blood-bank staff.</p>
    </Modal>
  );
}
