import { useState } from 'react';
import { useSelector } from 'react-redux';
import { UserCheck, Eye, Check, X, RotateCcw, FileText } from 'lucide-react';
import {
  usePatientVerificationDetailQuery, usePatientVerificationQueueQuery, useReviewPatientDocumentMutation,
} from '../../profile/patient/patientApi';
import { BLOOD_GROUPS, OVERALL_META, TRACK_META } from '../../profile/patient/patientProfileModel';
import { openPrivateFile } from '../../../lib/privateFiles';
import { parseApiError } from '../../../lib/apiError';
import { formatDate, formatDateTime, formatDay } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Select from '../../../components/ui/Select';
import EmptyState from '../../../components/ui/EmptyState';
import { Badge } from '../../../components/ui/Badge';

const DECISION_TEXT = {
  REJECT: { title: 'Reject document', button: 'Reject', help: 'Use when the document is invalid or does not belong to this patient.' },
  REQUEST_CHANGES: { title: 'Request a corrected document', button: 'Request changes', help: 'Use when the document is unclear, cut off or the wrong type.' },
};

/**
 * Admin review of patient identity and blood-group documents. The two tracks
 * are decided separately. Approving a blood-group document requires entering
 * the group printed on the document; the server refuses a mismatch with the profile.
 */
export default function PatientVerificationsPage() {
  const token = useSelector((s) => s.auth?.accessToken);
  const { data, isLoading } = usePatientVerificationQueueQuery();
  const queue = data?.data || [];
  const [selected, setSelected] = useState(null);
  const { data: detailData, isFetching } = usePatientVerificationDetailQuery(selected, { skip: !selected });
  const detail = detailData?.data;
  const [review, { isLoading: reviewing }] = useReviewPatientDocumentMutation();
  const [seenGroup, setSeenGroup] = useState('');
  const [dialog, setDialog] = useState(null); // { doc, decision }
  const [remarks, setRemarks] = useState('');

  const view = (id) => openPrivateFile(`/admin/patient-verifications/documents/${id}/file`, token)
    .catch((e) => toast.error(e.message));

  const decide = async (doc, decision, extra = {}) => {
    try {
      await review({ documentId: doc.id, decision, ...extra }).unwrap();
      toast.success(decision === 'APPROVE' ? 'Document approved' : 'Decision saved — the patient has been notified');
      setDialog(null);
      setRemarks('');
      setSeenGroup('');
    } catch (e) {
      toast.error(parseApiError(e).message);
    }
  };

  const renderDoc = (title, track, isBlood) => {
    const doc = track?.document;
    const meta = TRACK_META[track?.status || 'NOT_SUBMITTED'];
    return (
      <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="font-semibold">{title}</h3>
          <Badge tone={meta.tone} dot>{meta.label}</Badge>
        </div>
        {!doc ? <p className="text-sm text-slate-500">Not submitted.</p> : (
          <>
            <button type="button" onClick={() => view(doc.id)}
                    className="mb-2 flex w-full items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-left text-sm hover:bg-slate-100 dark:bg-slate-800 dark:hover:bg-slate-700">
              <FileText className="h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate">{doc.documentTypeLabel} · {doc.fileName}</span>
              <Eye className="h-4 w-4 text-ocean-600" aria-hidden="true" /><span className="text-xs font-semibold text-ocean-600">View</span>
            </button>
            <p className="text-xs text-slate-500">Uploaded {formatDateTime(doc.uploadedAt)}</p>
            {doc.reviewRemarks && <p className="mt-1 text-xs text-slate-600 dark:text-slate-300">Last remarks: {doc.reviewRemarks}</p>}
            {doc.status === 'PENDING' && (
              <div className="mt-3 space-y-2">
                {isBlood && (
                  <Select id={`seen-${doc.id}`} label="Blood group shown on the document *" value={seenGroup}
                          onChange={(e) => setSeenGroup(e.target.value)}
                          hint={`Profile says ${detail.profile.bloodGroup}. Approval is refused if they differ.`}>
                    <option value="">Select…</option>
                    {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
                  </Select>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" leftIcon={<Check className="h-4 w-4" />} loading={reviewing}
                          disabled={isBlood && !seenGroup}
                          onClick={() => decide(doc, 'APPROVE', isBlood ? { confirmedBloodGroup: seenGroup } : {})}>Approve</Button>
                  <Button size="sm" variant="secondary" leftIcon={<RotateCcw className="h-4 w-4" />}
                          onClick={() => { setRemarks(''); setDialog({ doc, decision: 'REQUEST_CHANGES' }); }}>Request changes</Button>
                  <Button size="sm" variant="ghost" leftIcon={<X className="h-4 w-4" />}
                          onClick={() => { setRemarks(''); setDialog({ doc, decision: 'REJECT' }); }}>Reject</Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader icon={UserCheck} breadcrumb="Home / Patient verifications" title="Patient verifications"
                  subtitle="Review identity and blood-group documents separately. Approving identity does not verify the blood group." />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <h2 className="mb-3 font-semibold">Awaiting review ({queue.length})</h2>
          {isLoading && <div className="space-y-2">{[...Array(3)].map((_, i) => <div key={i} className="skeleton h-14" />)}</div>}
          {!isLoading && queue.length === 0 && <EmptyState icon={UserCheck} title="All caught up" message="No patient documents are awaiting review." />}
          <ul className="space-y-2">
            {queue.map((p) => (
              <li key={p.id}>
                <button type="button" onClick={() => { setSelected(p.id); setSeenGroup(''); }} aria-current={selected === p.id}
                        className={'w-full rounded-xl border p-3 text-left text-sm transition ' + (selected === p.id
                          ? 'border-brand-300 bg-brand-50 dark:border-brand-800 dark:bg-brand-900/20'
                          : 'border-slate-100 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800')}>
                  <div className="font-semibold text-slate-900 dark:text-white">{p.fullName}</div>
                  <div className="mt-1 flex flex-wrap gap-1">
                    <Badge tone={TRACK_META[p.identity.status].tone}>ID: {TRACK_META[p.identity.status].label}</Badge>
                    <Badge tone={TRACK_META[p.bloodGroupVerification.status].tone}>Blood: {TRACK_META[p.bloodGroupVerification.status].label}</Badge>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        </Card>

        <Card className="lg:col-span-2">
          {!selected ? <p className="text-sm text-slate-500">Select a patient to review their documents.</p>
            : isFetching && !detail ? <div className="skeleton h-64" />
            : detail && (
              <div className="space-y-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <h2 className="text-lg font-bold">{detail.profile.fullName}</h2>
                    <p className="text-sm text-slate-500">{detail.profile.email} · {detail.profile.phone || 'no phone'}</p>
                  </div>
                  <Badge tone={OVERALL_META[detail.profile.verificationStatus].tone} dot>{OVERALL_META[detail.profile.verificationStatus].label}</Badge>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                  <div><dt className="text-xs text-slate-400">Date of birth</dt><dd>{formatDay(detail.profile.dateOfBirth)}</dd></div>
                  <div><dt className="text-xs text-slate-400">Blood group (profile)</dt><dd className="font-bold">{detail.profile.bloodGroup || '—'}</dd></div>
                  <div><dt className="text-xs text-slate-400">City</dt><dd>{detail.profile.city || '—'}</dd></div>
                  <div><dt className="text-xs text-slate-400">Missing fields</dt><dd>{detail.profile.missingFields.length ? detail.profile.missingFields.join(', ') : 'None'}</dd></div>
                </dl>
                <p className="text-xs text-slate-500">Check that the name and date of birth match the identity document, and that the blood group (ABO and Rh) on the medical document belongs to this patient.</p>
                <div className="grid gap-4 md:grid-cols-2">
                  {renderDoc('Identity document', detail.profile.identity, false)}
                  {renderDoc('Blood-group document', detail.profile.bloodGroupVerification, true)}
                </div>
                {detail.history.filter((h) => !h.current).length > 0 && (
                  <details className="text-sm">
                    <summary className="cursor-pointer font-semibold text-slate-600 dark:text-slate-300">Previous uploads</summary>
                    <ul className="mt-2 space-y-1 text-xs text-slate-500">
                      {detail.history.filter((h) => !h.current).map((h) => (
                        <li key={h.id}>
                          {formatDate(h.uploadedAt)} · {h.documentTypeLabel} · {TRACK_META[h.status]?.label}
                          {' '}<button type="button" className="font-semibold text-ocean-600 hover:underline" onClick={() => view(h.id)}>View</button>
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </div>
            )}
        </Card>
      </div>

      <Modal open={!!dialog} onClose={() => setDialog(null)} title={dialog ? DECISION_TEXT[dialog.decision].title : ''}
             footer={<>
               <Button variant="secondary" onClick={() => setDialog(null)}>Cancel</Button>
               <Button loading={reviewing} disabled={!remarks.trim()}
                       onClick={() => decide(dialog.doc, dialog.decision, { remarks })}>{dialog ? DECISION_TEXT[dialog.decision].button : ''}</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">{dialog && DECISION_TEXT[dialog.decision].help} The patient sees these remarks.</p>
        <label htmlFor="patient-review-remarks" className="mb-1.5 block text-sm font-medium">Remarks *</label>
        <textarea id="patient-review-remarks" rows={3} maxLength={1000} value={remarks} onChange={(e) => setRemarks(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900" />
      </Modal>
    </div>
  );
}
