import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import { ShieldCheck, Hospital, Building2, MapPin, Phone, Check, X, Mail, FileText } from 'lucide-react';
import { openPrivateFile } from '../../../lib/privateFiles';

import {
  usePendingVerificationsQuery,
  useVerifyHospitalMutation,
  useVerifyBloodBankMutation,
  useRejectHospitalMutation,
  useRejectBloodBankMutation,
} from '../adminApi';
import { parseApiError } from '../../../lib/apiError';
import { formatDate, formatDay } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import EmptyState from '../../../components/ui/EmptyState';
import { Badge } from '../../../components/ui/Badge';
import { OVERALL_STATUS } from '../../profile/hospital/documentStatus';
import DocumentRequirementsCard from '../components/DocumentRequirementsCard';

const REASONS = [
  'License number could not be verified',
  'Name does not match the license record',
  'License certificate is unreadable or incomplete',
  'License dates do not match the certificate',
  'Address or contact details are incomplete',
  'Duplicate registration',
];

/**
 * Admin verification queue: review each hospital and blood bank's details,
 * then approve (activates it and notifies the owner) or reject with a reason
 * (the owner is told why and can resubmit by editing their profile).
 */
export default function VerificationsPage() {
  const { data, isLoading } = usePendingVerificationsQuery();
  const [verifyHospital] = useVerifyHospitalMutation();
  const [verifyBank] = useVerifyBloodBankMutation();
  const [rejectHospital, { isLoading: rh }] = useRejectHospitalMutation();
  const [rejectBank, { isLoading: rb }] = useRejectBloodBankMutation();
  const [busyKey, setBusyKey] = useState(null);
  const [rejecting, setRejecting] = useState(null); // { kind, id, name }
  const [reason, setReason] = useState('');
  const token = useSelector((s) => s.auth?.accessToken);

  const hospitals = data?.data?.hospitals || [];
  const banks = data?.data?.bloodBanks || [];
  const nothing = !isLoading && hospitals.length === 0 && banks.length === 0;

  const approve = async (kind, id, name) => {
    setBusyKey(`${kind}-${id}`);
    try {
      await (kind === 'hospital' ? verifyHospital(id) : verifyBank(id)).unwrap();
      toast.success(`${name} verified`);
    } catch (err) {
      toast.error(parseApiError(err).message);
    } finally {
      setBusyKey(null);
    }
  };

  const confirmReject = async () => {
    try {
      const fn = rejecting.kind === 'hospital' ? rejectHospital : rejectBank;
      await fn({ id: rejecting.id, reason }).unwrap();
      toast.info(`${rejecting.name} rejected — the owner has been notified`);
      setRejecting(null);
    } catch (err) {
      toast.error(parseApiError(err).message);
    }
  };

  // License certificates are private: fetched with the admin's token, never via a public URL.
  const viewLicense = (bank) => openPrivateFile(`/admin/blood-banks/${bank.id}/license-document`, token)
    .catch((err) => toast.error(err.message));

  const renderItem = (kind, item, name) => (
    <div key={item.id} className="rounded-xl border border-slate-100 p-4 dark:border-slate-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-semibold text-slate-900 dark:text-white">{name}</div>
          <div className="mt-1 grid gap-x-6 gap-y-1 text-sm text-slate-500 sm:grid-cols-2">
            <span>License <b className="font-mono text-slate-700 dark:text-slate-200">{item.licenseNumber}</b></span>
            <span className="inline-flex items-center gap-1"><Phone className="h-3.5 w-3.5" /> {item.phone || 'No phone'}</span>
            <span className="inline-flex items-center gap-1 sm:col-span-2"><MapPin className="h-3.5 w-3.5 shrink-0" />
              {[item.address, item.city, item.state, item.pincode].filter(Boolean).join(', ') || 'No address'}
            </span>
            <span>Location {item.latitude != null ? `${Number(item.latitude).toFixed(4)}, ${Number(item.longitude).toFixed(4)}` : 'not set'}</span>
            <span>Submitted {formatDate(item.licenseSubmittedAt || item.createdAt)}</span>
            {kind === 'bank' && (
              <>
                <span className="inline-flex items-center gap-1"><Mail className="h-3.5 w-3.5" /> {item.contactEmail || 'No email'}</span>
                <span>Issued {formatDay(item.licenseIssueDate)} · Expires {formatDay(item.licenseExpiryDate)}</span>
              </>
            )}
          </div>
          {kind === 'bank' && (
            <button type="button" onClick={() => viewLicense(item)}
                    className="mt-3 inline-flex max-w-full items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700">
              <FileText className="h-4 w-4 shrink-0 text-brand-600" />
              <span className="truncate">{item.licenseDocumentName || 'License certificate'}</span>
              <span className="shrink-0 text-xs font-semibold text-ocean-600">View certificate</span>
            </button>
          )}
          {kind === 'hospital' && item.verificationStatus && (
            <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
              <Badge tone={(OVERALL_STATUS[item.verificationStatus] || OVERALL_STATUS.DOCUMENTS_PENDING).tone} dot>
                {(OVERALL_STATUS[item.verificationStatus] || OVERALL_STATUS.DOCUMENTS_PENDING).label}
              </Badge>
              {item.pendingDocuments > 0 && <Badge tone="warning">{item.pendingDocuments} document(s) to review</Badge>}
              {item.verified && <Badge tone="ocean">Already approved — reviewing changed documents</Badge>}
              <span className="w-full text-slate-500">{item.verificationMessage}</span>
            </div>
          )}
          <div className="mt-2 flex flex-wrap gap-4">
            {kind === 'hospital' && (
              <Link to={`/admin/verifications/hospitals/${item.id}`} className="inline-flex items-center gap-1 text-xs font-semibold text-brand-600 hover:underline">
                <FileText className="h-3.5 w-3.5" /> Review documents →
              </Link>
            )}
            <Link to={`/admin/users/${item.userId}`} className="inline-block text-xs font-semibold text-ocean-600 hover:underline">View account →</Link>
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          {!(kind === 'hospital' && item.verified) && (
            <Button size="sm" leftIcon={<Check className="h-4 w-4" />} loading={busyKey === `${kind}-${item.id}`}
                    disabled={busyKey !== null || (kind === 'hospital' && item.documentsComplete === false)}
                    title={kind === 'hospital' && item.documentsComplete === false ? 'Approve all mandatory documents first' : undefined}
                    onClick={() => approve(kind, item.id, name)}>Verify</Button>
          )}
          <Button size="sm" variant="secondary" leftIcon={<X className="h-4 w-4" />} disabled={busyKey !== null}
                  onClick={() => { setReason(''); setRejecting({ kind, id: item.id, name }); }}>Reject</Button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="space-y-6">
      <PageHeader icon={ShieldCheck} breadcrumb="Home / Verifications" title="Pending verifications"
                  subtitle="Check each organisation's details, then verify or reject with a reason." />

      {isLoading && <div className="space-y-3">{[...Array(3)].map((_, i) => <div key={i} className="skeleton h-24" />)}</div>}

      {nothing && <EmptyState icon={ShieldCheck} title="All caught up" message="No hospitals or blood banks are awaiting verification." />}

      {hospitals.length > 0 && (
        <Card>
          <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Hospital className="h-5 w-5 text-brand-600" /> Hospitals ({hospitals.length})</h2>
          <div className="space-y-3">{hospitals.map((h) => renderItem('hospital', h, h.hospitalName))}</div>
        </Card>
      )}

      {banks.length > 0 && (
        <Card>
          <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Building2 className="h-5 w-5 text-ocean-600" /> Blood banks ({banks.length})</h2>
          <div className="space-y-3">{banks.map((b) => renderItem('bank', b, b.bankName))}</div>
        </Card>
      )}

      <DocumentRequirementsCard />

      <Modal open={!!rejecting} onClose={() => setRejecting(null)} title={`Reject ${rejecting?.name || ''}`}
             footer={<>
               <Button variant="secondary" onClick={() => setRejecting(null)}>Cancel</Button>
               <Button loading={rh || rb} disabled={!reason.trim()} onClick={confirmReject}>Reject verification</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">The owner sees this reason and can resubmit by correcting their profile.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button key={r} type="button" onClick={() => setReason(r)}
                    className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300">{r}</button>
          ))}
        </div>
        <label htmlFor="reject-reason" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Reason *</label>
        <textarea id="reject-reason" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
      </Modal>
    </div>
  );
}
