import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Link, useParams } from 'react-router-dom';
import { Hospital, Check, X, Eye, Download, FilePlus2, FileText, History, ArrowLeft, Phone, MapPin, ShieldCheck } from 'lucide-react';
import {
  useHospitalReviewQuery,
  useApproveHospitalDocumentMutation,
  useRejectHospitalDocumentMutation,
  useRequestHospitalDocumentMutation,
  useCancelHospitalDocumentRequestMutation,
} from '../hospitalDocumentsAdminApi';
import { useVerifyHospitalMutation, useRejectHospitalMutation } from '../adminApi';
import { openPrivateFile, downloadPrivateFile } from '../../../lib/privateFiles';
import { parseApiError } from '../../../lib/apiError';
import { formatDate, formatDateTime } from '../../../lib/format';
import { toast } from '../../../lib/toast';
import PageHeader from '../../../components/layout/PageHeader';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import Alert from '../../../components/ui/Alert';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';
import { DOC_STATUS, OVERALL_STATUS, formatBytes, adminDocPath } from '../../profile/hospital/documentStatus';

const DOC_REASONS = [
  'Document is unreadable or incomplete',
  'Name does not match the hospital profile',
  'Registration / license number does not match',
  'Document has expired or dates are wrong',
  'Wrong document type uploaded',
];

const REQUEST_TYPES = [
  { value: 'REGISTRATION_CERTIFICATE', label: 'Corrected registration certificate' },
  { value: 'OPERATING_LICENSE', label: 'Corrected operating license / registration proof' },
  { value: 'BLOOD_BANK_LICENSE', label: 'Corrected blood bank license' },
  { value: 'ACCREDITATION_CERTIFICATE', label: 'Accreditation certificate' },
  { value: 'SUPPORTING_DOCUMENT', label: 'Supporting document' },
  { value: 'ADDITIONAL_REQUESTED', label: 'Other additional document (name it)' },
];

const textareaCls = 'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-900';

/**
 * Admin review of one hospital: details, overall status, each submitted
 * document (preview/download, approve, reject with mandatory remarks),
 * document requests and the complete submission history. Final hospital
 * approval is enabled only once the server reports all mandatory documents approved.
 */
export default function HospitalReviewPage() {
  const { id } = useParams();
  const token = useSelector((s) => s.auth?.accessToken);
  const { data, isLoading, isError } = useHospitalReviewQuery(id);
  const [approveDoc] = useApproveHospitalDocumentMutation();
  const [rejectDoc, { isLoading: rejectingDoc }] = useRejectHospitalDocumentMutation();
  const [requestDoc, { isLoading: requesting }] = useRequestHospitalDocumentMutation();
  const [cancelRequest] = useCancelHospitalDocumentRequestMutation();
  const [verifyHospital, { isLoading: verifying }] = useVerifyHospitalMutation();
  const [rejectHospital, { isLoading: rejectingHospital }] = useRejectHospitalMutation();

  const [busy, setBusy] = useState(null);
  const [rejecting, setRejecting] = useState(null); // document
  const [remarks, setRemarks] = useState('');
  const [requestCorrection, setRequestCorrection] = useState(true);
  const [requestOpen, setRequestOpen] = useState(false);
  const [reqForm, setReqForm] = useState({ documentType: 'SUPPORTING_DOCUMENT', title: '', reason: '' });
  const [hospitalReject, setHospitalReject] = useState(false);
  const [hospitalReason, setHospitalReason] = useState('');

  const review = data?.data;
  const h = review?.hospital;
  const c = review?.compliance;

  const act = async (key, fn, success) => {
    setBusy(key);
    try { await fn(); toast.success(success); } catch (err) { toast.error(parseApiError(err).message); } finally { setBusy(null); }
  };
  const view = (d) => openPrivateFile(adminDocPath(d.id), token).catch((e) => toast.error(e.message));
  const download = (d) => downloadPrivateFile(adminDocPath(d.id, true), token, d.fileName).catch((e) => toast.error(e.message));

  const confirmReject = async () => {
    try {
      await rejectDoc({ id: rejecting.id, remarks, requestCorrection }).unwrap();
      toast.info('Document rejected — the hospital has been notified');
      setRejecting(null);
    } catch (err) { toast.error(parseApiError(err).message); }
  };
  const submitRequest = async () => {
    try {
      await requestDoc({ hospitalId: Number(id), ...reqForm }).unwrap();
      toast.success('Document requested — the hospital has been notified');
      setRequestOpen(false);
      setReqForm({ documentType: 'SUPPORTING_DOCUMENT', title: '', reason: '' });
    } catch (err) { toast.error(parseApiError(err).message); }
  };
  const confirmHospitalReject = async () => {
    try {
      await rejectHospital({ id: Number(id), reason: hospitalReason }).unwrap();
      toast.info('Hospital verification rejected');
      setHospitalReject(false);
    } catch (err) { toast.error(parseApiError(err).message); }
  };

  if (isLoading) return <div className="space-y-4">{[...Array(4)].map((_, i) => <Skeleton key={i} className="h-28" />)}</div>;
  if (isError || !review) return <Alert type="error">Could not load this hospital. It may not exist.</Alert>;

  const overall = OVERALL_STATUS[c.status] || OVERALL_STATUS.DOCUMENTS_PENDING;
  const pendingDocs = review.documents.filter((d) => d.status === 'PENDING');

  return (
    <div className="space-y-6">
      <PageHeader icon={Hospital} breadcrumb="Home / Verifications / Hospital" title={h.hospitalName}
                  subtitle="Review each document, then approve the hospital once every mandatory document is approved."
                  actions={<Link to="/admin/verifications" className="inline-flex items-center gap-1 text-sm font-semibold text-ocean-600 hover:underline"><ArrowLeft className="h-4 w-4" /> Back to queue</Link>} />

      {/* Hospital details + status */}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-3 text-lg font-semibold">Hospital details</h2>
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <Detail label="License number"><span className="font-mono">{h.licenseNumber}</span></Detail>
            <Detail label="Registration category">{h.registrationCategory?.replaceAll('_', ' ').toLowerCase() || 'Not set'}</Detail>
            <Detail label="Own blood bank">{h.operatesBloodBank ? 'Yes — blood bank license required' : 'No (requests blood only)'}</Detail>
            <Detail label="Phone"><span className="inline-flex items-center gap-1"><Phone className="h-3.5 w-3.5" />{h.phone || '—'}</span></Detail>
            <Detail label="Address" wide>
              <span className="inline-flex items-start gap-1"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                {[h.address, h.city, h.state, h.pincode].filter(Boolean).join(', ') || '—'}</span>
            </Detail>
            <Detail label="Registered">{formatDate(h.createdAt)}</Detail>
            <Detail label="In-progress requests">{review.activeRequests}</Detail>
          </dl>
          <Link to={`/admin/users/${h.userId}`} className="mt-3 inline-block text-xs font-semibold text-ocean-600 hover:underline">View account →</Link>
        </Card>

        <Card>
          <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold"><ShieldCheck className="h-5 w-5 text-brand-600" /> Status</h2>
          <Badge tone={overall.tone} dot>{overall.label}</Badge>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{c.message}</p>
          <ul className="mt-3 space-y-1 text-xs text-slate-500">
            <li>Admin approval: <b>{h.verified ? `approved ${formatDate(h.verifiedAt)}` : h.rejectionReason ? 'rejected' : 'pending'}</b></li>
            <li>Mandatory documents: <b>{c.documentsComplete ? 'all approved' : `outstanding — ${c.outstanding.join(', ')}`}</b></li>
            <li>Profile: <b>{c.profileComplete ? 'complete' : `missing ${c.missingProfileFields.join(', ')}`}</b></li>
            {c.inLegacyGrace && <li className="text-amber-700">Transition window until {formatDate(c.legacyGraceUntil)}</li>}
          </ul>
          <div className="mt-4 flex flex-wrap gap-2">
            {!h.verified && (
              <Button size="sm" leftIcon={<Check className="h-4 w-4" />} loading={verifying} disabled={!c.documentsComplete}
                      title={c.documentsComplete ? undefined : 'All mandatory documents must be approved first'}
                      onClick={() => act('hospital', () => verifyHospital(Number(id)).unwrap(), `${h.hospitalName} verified`)}>
                Approve hospital
              </Button>
            )}
            <Button size="sm" variant="secondary" leftIcon={<X className="h-4 w-4" />}
                    onClick={() => { setHospitalReason(''); setHospitalReject(true); }}>Reject hospital</Button>
          </div>
          {!h.verified && !c.documentsComplete && (
            <p className="mt-2 text-xs text-slate-500">Approval unlocks when every mandatory document is approved and valid.</p>
          )}
        </Card>
      </div>

      {/* Requirements overview */}
      <Card>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">Required documents</h2>
          <Button size="sm" variant="soft" leftIcon={<FilePlus2 className="h-4 w-4" />} onClick={() => setRequestOpen(true)}>Request document</Button>
        </div>
        <ul className="grid gap-2 sm:grid-cols-2">
          {c.requirements.map((r) => {
            const m = DOC_STATUS[r.state] || DOC_STATUS.NOT_SUBMITTED;
            return (
              <li key={`${r.requirementId}-${r.requestId}`} className="rounded-xl border border-slate-100 px-3 py-2 text-sm dark:border-slate-800">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{r.label}</span>
                  <Badge tone={r.mandatory ? 'brand' : 'neutral'}>{r.requestId ? 'Requested' : r.mandatory ? 'Required' : 'Optional'}</Badge>
                  <Badge tone={m.tone} dot>{m.label}</Badge>
                </div>
                <p className="mt-1 text-xs text-slate-500">{r.explanation}</p>
              </li>
            );
          })}
        </ul>
      </Card>

      {/* Current documents */}
      <Card>
        <h2 className="mb-3 text-lg font-semibold">Submitted documents {pendingDocs.length > 0 && <Badge tone="warning">{pendingDocs.length} to review</Badge>}</h2>
        {review.documents.length === 0 ? (
          <p className="text-sm text-slate-500">The hospital has not uploaded any documents yet.</p>
        ) : (
          <div className="space-y-3">
            {review.documents.map((d) => {
              const m = DOC_STATUS[d.status] || DOC_STATUS.PENDING;
              return (
                <div key={d.id} className="rounded-xl border border-slate-100 p-4 dark:border-slate-800">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <FileText className="h-4 w-4 text-brand-600" aria-hidden="true" />
                        <span className="font-semibold">{d.documentTypeLabel}</span>
                        <Badge tone={m.tone} dot>{m.label}</Badge>
                      </div>
                      <p className="mt-1 truncate text-sm text-slate-600 dark:text-slate-300">{d.fileName} · {formatBytes(d.fileSize)}</p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        Uploaded {formatDateTime(d.uploadedAt)} · Issued {formatDate(d.issueDate)} · Expires {formatDate(d.expiryDate)}
                        {d.reviewedAt && <> · Reviewed {formatDateTime(d.reviewedAt)} by admin #{d.reviewedBy}</>}
                      </p>
                      {d.reviewRemarks && <p className="mt-1 text-xs text-slate-600 dark:text-slate-300">Remarks: {d.reviewRemarks}</p>}
                      <p className="mt-0.5 break-all font-mono text-[10px] text-slate-400" title="SHA-256 of the stored file">sha256 {d.sha256}</p>
                      <div className="mt-2 flex gap-3 text-xs font-semibold">
                        <button type="button" onClick={() => view(d)} className="inline-flex items-center gap-1 text-ocean-600 hover:underline"><Eye className="h-3.5 w-3.5" /> Preview</button>
                        <button type="button" onClick={() => download(d)} className="inline-flex items-center gap-1 text-ocean-600 hover:underline"><Download className="h-3.5 w-3.5" /> Download</button>
                      </div>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      {(d.status === 'PENDING' || d.status === 'REJECTED') && (
                        <Button size="sm" leftIcon={<Check className="h-4 w-4" />} loading={busy === `a-${d.id}`} disabled={busy !== null}
                                onClick={() => act(`a-${d.id}`, () => approveDoc({ id: d.id }).unwrap(), 'Document approved')}>Approve</Button>
                      )}
                      {d.status !== 'REJECTED' && (
                        <Button size="sm" variant="secondary" leftIcon={<X className="h-4 w-4" />} disabled={busy !== null}
                                onClick={() => { setRemarks(''); setRequestCorrection(true); setRejecting(d); }}>
                          {d.status === 'VERIFIED' ? 'Revoke' : 'Reject'}
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {/* Requests */}
      {review.requests.length > 0 && (
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Document requests</h2>
          <ul className="space-y-2 text-sm">
            {review.requests.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-100 px-3 py-2 dark:border-slate-800">
                <span className="font-medium">{r.label}</span>
                <Badge tone={r.status === 'FULFILLED' ? 'success' : r.status === 'CANCELLED' ? 'neutral' : 'warning'}>{r.status}</Badge>
                <span className="text-xs text-slate-500">Requested {formatDateTime(r.requestedAt)}</span>
                {(r.status === 'OPEN' || r.status === 'SUBMITTED') && (
                  <button type="button" className="ml-auto text-xs font-semibold text-brand-600 hover:underline"
                          onClick={() => act(`c-${r.id}`, () => cancelRequest(r.id).unwrap(), 'Request withdrawn')}>Withdraw</button>
                )}
                <span className="w-full text-xs text-slate-600 dark:text-slate-300">{r.reason}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {/* History */}
      <Card>
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold"><History className="h-5 w-5" /> Submission history</h2>
        {review.history.length === 0 ? <p className="text-sm text-slate-500">No submissions yet.</p> : (
          <Table>
            <THead><TH>Uploaded</TH><TH>Document</TH><TH>File</TH><TH>Status</TH><TH>Review</TH><TH>State</TH><TH /></THead>
            <TBody>
              {review.history.map((d) => (
                <TR key={d.id}>
                  <TD className="whitespace-nowrap">{formatDateTime(d.uploadedAt)}</TD>
                  <TD>{d.documentTypeLabel}</TD>
                  <TD className="max-w-[12rem] truncate">{d.fileName}</TD>
                  <TD><Badge tone={(DOC_STATUS[d.status] || DOC_STATUS.PENDING).tone}>{(DOC_STATUS[d.status] || DOC_STATUS.PENDING).label}</Badge></TD>
                  <TD className="text-xs">{d.reviewedAt ? `${formatDateTime(d.reviewedAt)} — ${d.reviewRemarks || 'no remarks'}` : '—'}</TD>
                  <TD className="text-xs">{d.current ? 'Current' : d.removedAt ? `Removed ${formatDate(d.removedAt)}` : `Replaced ${formatDate(d.supersededAt)}`}</TD>
                  <TD><button type="button" onClick={() => view(d)} className="text-xs font-semibold text-ocean-600 hover:underline">View</button></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {/* Reject document */}
      <Modal open={!!rejecting} onClose={() => setRejecting(null)} title={`${rejecting?.status === 'VERIFIED' ? 'Revoke approval' : 'Reject'}: ${rejecting?.documentTypeLabel || ''}`}
             footer={<>
               <Button variant="secondary" onClick={() => setRejecting(null)}>Cancel</Button>
               <Button loading={rejectingDoc} disabled={!remarks.trim()} onClick={confirmReject}>Reject document</Button>
             </>}>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">The hospital sees these remarks and must upload a corrected document.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {DOC_REASONS.map((r) => (
            <button key={r} type="button" onClick={() => setRemarks(r)}
                    className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300">{r}</button>
          ))}
        </div>
        <label htmlFor="doc-reject-remarks" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Remarks *</label>
        <textarea id="doc-reject-remarks" rows={3} maxLength={1000} value={remarks} onChange={(e) => setRemarks(e.target.value)} className={textareaCls} />
        {!rejecting?.requestId && (
          <label className="mt-3 flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
            <input type="checkbox" checked={requestCorrection} onChange={(e) => setRequestCorrection(e.target.checked)} className="h-4 w-4 accent-brand-600" />
            Also request a corrected document
          </label>
        )}
      </Modal>

      {/* Request document */}
      <Modal open={requestOpen} onClose={() => setRequestOpen(false)} title="Request a document"
             footer={<>
               <Button variant="secondary" onClick={() => setRequestOpen(false)}>Cancel</Button>
               <Button loading={requesting} disabled={!reqForm.reason.trim() || (reqForm.documentType === 'ADDITIONAL_REQUESTED' && !reqForm.title.trim())}
                       onClick={submitRequest}>Send request</Button>
             </>}>
        <Alert type="warning">
          Requested documents are mandatory: until the hospital uploads it and you approve it, the hospital is not fully
          verified and cannot act on restricted requests.
        </Alert>
        <div className="space-y-3">
          <Select id="req-type" label="Document" value={reqForm.documentType}
                  onChange={(e) => setReqForm({ ...reqForm, documentType: e.target.value })}>
            {REQUEST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </Select>
          {reqForm.documentType === 'ADDITIONAL_REQUESTED' && (
            <TextField id="req-title" label="Document name *" maxLength={150} value={reqForm.title}
                       onChange={(e) => setReqForm({ ...reqForm, title: e.target.value })} />
          )}
          <div>
            <label htmlFor="req-reason" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">What is needed and why *</label>
            <textarea id="req-reason" rows={3} maxLength={1000} value={reqForm.reason}
                      onChange={(e) => setReqForm({ ...reqForm, reason: e.target.value })} className={textareaCls} />
          </div>
        </div>
      </Modal>

      {/* Reject hospital */}
      <Modal open={hospitalReject} onClose={() => setHospitalReject(false)} title={`Reject ${h.hospitalName}`}
             footer={<>
               <Button variant="secondary" onClick={() => setHospitalReject(false)}>Cancel</Button>
               <Button loading={rejectingHospital} disabled={!hospitalReason.trim()} onClick={confirmHospitalReject}>Reject verification</Button>
             </>}>
        <label htmlFor="hospital-reject-reason" className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">Reason *</label>
        <textarea id="hospital-reject-reason" rows={3} maxLength={500} value={hospitalReason}
                  onChange={(e) => setHospitalReason(e.target.value)} className={textareaCls} />
      </Modal>
    </div>
  );
}

function Detail({ label, children, wide }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="text-slate-700 dark:text-slate-200">{children}</dd>
    </div>
  );
}
