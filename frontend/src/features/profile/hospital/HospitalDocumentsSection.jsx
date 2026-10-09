import { useMemo, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { FileCheck2, FileText, Eye, Download, RefreshCw, Trash2, Upload, History, MessageSquareWarning } from 'lucide-react';
import { apiSlice } from '../../../app/apiSlice';
import { useMyHospitalDocumentsQuery, useRemoveHospitalDocumentMutation } from './hospitalDocumentsApi';
import { uploadWithProgress, openPrivateFile, downloadPrivateFile } from '../../../lib/privateFiles';
import { parseApiError } from '../../../lib/apiError';
import { formatDate, formatDateTime } from '../../../lib/format';
import { notify } from '../../../lib/toast';
import Card from '../../../components/ui/Card';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import TextField from '../../../components/ui/TextField';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';
import Skeleton from '../../../components/ui/Skeleton';
import { Badge } from '../../../components/ui/Badge';
import DocumentDropzone from './DocumentDropzone';
import { DOC_STATUS, formatBytes, hospitalDocPath } from './documentStatus';

const EMPTY_UPLOAD = { target: '', issueDate: '', expiryDate: '', replacesDocumentId: null, file: null };

/**
 * Hospital Documents & Verification: required/optional documents with their
 * individual statuses, administrator requests, upload (drag-and-drop, progress,
 * validation), preview/download, replace/remove and the full submission history.
 * Statuses come only from the server; uploading never sets a status.
 */
export default function HospitalDocumentsSection() {
  const { data, isLoading, isError, refetch } = useMyHospitalDocumentsQuery();
  const [removeDoc, { isLoading: removing }] = useRemoveHospitalDocumentMutation();
  const token = useSelector((s) => s.auth?.accessToken);
  const dispatch = useDispatch();
  const uploadRef = useRef(null);

  const [upload, setUpload] = useState(EMPTY_UPLOAD);
  const [progress, setProgress] = useState(null);
  const [uploadErr, setUploadErr] = useState({ message: '', fieldErrors: {} });
  const [confirmRemove, setConfirmRemove] = useState(null);

  const overview = data?.data;
  const compliance = overview?.compliance;
  const rules = overview?.rules;
  const docsById = useMemo(() => Object.fromEntries((overview?.history || []).map((d) => [d.id, d])), [overview]);
  const openRequests = (overview?.requests || []).filter((r) => r.status === 'OPEN' || r.status === 'SUBMITTED');

  // What the type selector offers: configured types plus open administrator requests.
  const targets = useMemo(() => {
    const list = (rules?.types || []).map((t) => ({
      key: `type:${t.type}`, label: `${t.label}${t.mandatory ? ' (required)' : ' (optional)'}`,
      type: t.type, expiryDateRequired: t.expiryDateRequired,
    }));
    openRequests.forEach((r) => list.push({
      key: `req:${r.id}`, label: `Requested: ${r.label}`, type: r.documentType, requestId: r.id, expiryDateRequired: false,
    }));
    return list;
  }, [rules, openRequests]);
  const target = targets.find((t) => t.key === upload.target);
  const replacing = upload.replacesDocumentId ? docsById[upload.replacesDocumentId] : null;

  const startUpload = (key, replacesDocumentId = null) => {
    setUpload({ ...EMPTY_UPLOAD, target: key, replacesDocumentId });
    setUploadErr({ message: '', fieldErrors: {} });
    uploadRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const submitUpload = async (e) => {
    e.preventDefault();
    setUploadErr({ message: '', fieldErrors: {} });
    if (!target) { setUploadErr({ message: '', fieldErrors: { documentType: 'Select a document type' } }); return; }
    if (!upload.file) { setUploadErr({ message: '', fieldErrors: { file: 'Choose a file to upload' } }); return; }
    if (target.expiryDateRequired && !upload.expiryDate) {
      setUploadErr({ message: '', fieldErrors: { expiryDate: 'Expiry date is required for this document' } });
      return;
    }
    setProgress(0);
    try {
      await uploadWithProgress('/hospitals/me/documents', upload.file, token, setProgress, {
        documentType: target.requestId ? undefined : target.type,
        requestId: target.requestId,
        issueDate: upload.issueDate,
        expiryDate: upload.expiryDate,
        replacesDocumentId: upload.replacesDocumentId,
      });
      notify.success('Document uploaded — it is now pending administrator verification');
      setUpload(EMPTY_UPLOAD);
      dispatch(apiSlice.util.invalidateTags(['HospitalDocs', 'User']));
    } catch (err) {
      setUploadErr({ message: err.message, fieldErrors: err.fieldErrors || {} });
    } finally {
      setProgress(null);
    }
  };

  const doRemove = async () => {
    try {
      await removeDoc(confirmRemove.id).unwrap();
      notify.success('Document removed. It stays in your submission history.');
      setConfirmRemove(null);
    } catch (err) {
      notify.error(parseApiError(err).message);
    }
  };

  const view = (d) => openPrivateFile(hospitalDocPath(d.id), token).catch((e) => notify.error(e.message));
  const download = (d) => downloadPrivateFile(hospitalDocPath(d.id, true), token, d.fileName).catch((e) => notify.error(e.message));

  return (
    <Card className="mt-6" id="hospital-documents">
      <div className="mb-1 flex flex-wrap items-center gap-2">
        <FileCheck2 className="h-5 w-5 text-brand-600" aria-hidden="true" />
        <h2 className="text-lg font-bold text-slate-900 dark:text-white">Documents &amp; Verification</h2>
      </div>
      <p className="mb-5 text-sm text-slate-500">
        Upload the documents that apply to your hospital. Each one is reviewed by a platform administrator.
        A blood bank license is required only if your hospital runs its own blood bank.
      </p>

      {isLoading && <div className="space-y-3">{[...Array(3)].map((_, i) => <Skeleton key={i} className="h-20" />)}</div>}
      {isError && (
        <Alert type="error">Could not load your documents. <button type="button" className="font-semibold underline" onClick={refetch}>Retry</button></Alert>
      )}

      {overview && (
        <>
          {/* Administrator requests */}
          {openRequests.length > 0 && (
            <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 p-4 dark:border-red-900/60 dark:bg-red-950/30">
              <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-brand-800 dark:text-red-200">
                <MessageSquareWarning className="h-4 w-4" aria-hidden="true" /> Requested by an administrator
              </p>
              <ul className="space-y-2">
                {openRequests.map((r) => (
                  <li key={r.id} className="flex flex-wrap items-center gap-2 rounded-xl bg-white/80 px-3 py-2 text-sm dark:bg-slate-900/60">
                    <span className="font-medium text-slate-900 dark:text-white">{r.label}</span>
                    <Badge tone={r.status === 'SUBMITTED' ? 'warning' : 'brand'}>{r.status === 'SUBMITTED' ? 'Submitted — under review' : 'Awaiting upload'}</Badge>
                    <span className="w-full text-xs text-slate-600 dark:text-slate-300">{r.reason}</span>
                    {r.status === 'OPEN' && (
                      <Button size="sm" variant="soft" leftIcon={<Upload className="h-4 w-4" />} onClick={() => startUpload(`req:${r.id}`)}>Upload</Button>
                    )}
                    {r.documentType === 'ADDITIONAL_REQUESTED' && overview.documents.filter((d) => d.requestId === r.id).map((d) => (
                      <div key={d.id} className="w-full">
                        <DocumentRow doc={d} onView={() => view(d)} onDownload={() => download(d)}
                                     onReplace={() => startUpload(`req:${r.id}`, d.id)} onRemove={() => setConfirmRemove(d)} />
                      </div>
                    ))}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Requirements checklist */}
          <ul className="space-y-3" aria-label="Hospital documents">
            {compliance.requirements.filter((i) => !i.requestId).map((item) => {
              const meta = DOC_STATUS[item.state] || DOC_STATUS.NOT_SUBMITTED;
              const itemDocs = item.documentIds.map((id) => docsById[id]).filter(Boolean);
              return (
                <li key={`${item.documentType}-${item.requirementId}`}
                    className="rounded-2xl border border-slate-100 p-4 dark:border-slate-800">
                  <div className="flex flex-wrap items-start gap-2">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-semibold text-slate-900 dark:text-white">{item.label}</h3>
                        <Badge tone={item.mandatory ? 'brand' : 'neutral'}>{item.mandatory ? 'Required' : 'Optional'}</Badge>
                        <Badge tone={meta.tone} dot>{meta.label}</Badge>
                      </div>
                      {item.description && <p className="mt-1 text-xs text-slate-500">{item.description}</p>}
                      <p className={`mt-1 text-sm ${item.state === 'REJECTED' || item.state === 'EXPIRED' ? 'font-medium text-brand-700 dark:text-red-300' : 'text-slate-600 dark:text-slate-300'}`}>
                        {item.explanation}
                      </p>
                    </div>
                    {(itemDocs.length === 0 || item.multiple) && (
                      <Button size="sm" variant={item.mandatory && itemDocs.length === 0 ? 'primary' : 'secondary'}
                              leftIcon={<Upload className="h-4 w-4" />} onClick={() => startUpload(`type:${item.documentType}`)}>
                        {itemDocs.length === 0 ? 'Upload' : 'Add another'}
                      </Button>
                    )}
                  </div>

                  {itemDocs.map((d) => (
                    <DocumentRow key={d.id} doc={d} onView={() => view(d)} onDownload={() => download(d)}
                                 onReplace={() => startUpload(`type:${d.documentType}`, d.id)}
                                 onRemove={() => setConfirmRemove(d)} />
                  ))}
                </li>
              );
            })}
          </ul>

          {/* Upload panel */}
          <form ref={uploadRef} onSubmit={submitUpload} noValidate
                className="mt-6 rounded-2xl border border-slate-200 p-4 dark:border-slate-700" aria-labelledby="upload-heading">
            <h3 id="upload-heading" className="mb-3 font-semibold text-slate-900 dark:text-white">
              {replacing ? `Replace: ${replacing.fileName}` : 'Upload a document'}
            </h3>
            {replacing?.status === 'VERIFIED' && (
              <Alert type="warning">
                This document is currently approved. Replacing it sends the new file for re-verification, and your hospital
                will not be fully verified until an administrator approves it.
              </Alert>
            )}
            <Alert type="error">{uploadErr.message}</Alert>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Select id="doc-type" label="Document type *" className="sm:col-span-3" value={upload.target}
                      disabled={!!replacing || progress != null} error={uploadErr.fieldErrors.documentType}
                      onChange={(e) => setUpload({ ...upload, target: e.target.value, replacesDocumentId: null })}>
                <option value="">Select…</option>
                {targets.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
              </Select>
              <TextField id="doc-issue" label="Issue date" type="date" value={upload.issueDate}
                         max={new Date().toISOString().slice(0, 10)} disabled={progress != null}
                         error={uploadErr.fieldErrors.issueDate}
                         onChange={(e) => setUpload({ ...upload, issueDate: e.target.value })} />
              <TextField id="doc-expiry" label={`Expiry date${target?.expiryDateRequired ? ' *' : ''}`} type="date"
                         value={upload.expiryDate} disabled={progress != null}
                         hint={target?.expiryDateRequired ? 'Required for this document' : 'If the document has one'}
                         error={uploadErr.fieldErrors.expiryDate}
                         onChange={(e) => setUpload({ ...upload, expiryDate: e.target.value })} />
            </div>

            <div className="mt-4">
              <DocumentDropzone file={upload.file} maxBytes={rules?.maxFileSizeBytes} progress={progress}
                                error={uploadErr.fieldErrors.file} disabled={progress != null}
                                onFile={(file) => setUpload({ ...upload, file })} />
            </div>

            <div className="mt-4 flex flex-wrap gap-2">
              <Button type="submit" loading={progress != null} leftIcon={<Upload className="h-4 w-4" />}>Upload for verification</Button>
              {(upload.target || upload.file) && progress == null && (
                <Button type="button" variant="secondary" onClick={() => { setUpload(EMPTY_UPLOAD); setUploadErr({ message: '', fieldErrors: {} }); }}>Cancel</Button>
              )}
            </div>
          </form>

          {/* History */}
          <details className="mt-6 rounded-2xl border border-slate-100 p-4 dark:border-slate-800">
            <summary className="flex cursor-pointer items-center gap-2 font-semibold text-slate-800 dark:text-slate-100">
              <History className="h-4 w-4" aria-hidden="true" /> Submission history ({overview.history.length})
            </summary>
            {overview.history.length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">Nothing uploaded yet.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {overview.history.map((d) => (
                  <li key={d.id} className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-medium text-slate-800 dark:text-slate-100">{d.documentTypeLabel}</span>
                    <span className="truncate text-slate-500">{d.fileName}</span>
                    <Badge tone={(DOC_STATUS[d.status] || DOC_STATUS.PENDING).tone}>{(DOC_STATUS[d.status] || DOC_STATUS.PENDING).label}</Badge>
                    {!d.current && <Badge tone="neutral">{d.removedAt ? `Removed ${formatDate(d.removedAt)}` : `Replaced ${formatDate(d.supersededAt)}`}</Badge>}
                    <span className="text-xs text-slate-400">Uploaded {formatDateTime(d.uploadedAt)}</span>
                    {d.reviewRemarks && <span className="w-full text-xs text-slate-500">Remarks: {d.reviewRemarks}</span>}
                  </li>
                ))}
              </ul>
            )}
          </details>
        </>
      )}

      <Modal open={!!confirmRemove} onClose={() => setConfirmRemove(null)} title="Remove document?"
             footer={<>
               <Button variant="secondary" onClick={() => setConfirmRemove(null)}>Cancel</Button>
               <Button variant="danger" loading={removing} onClick={doRemove}>Remove</Button>
             </>}>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          <b>{confirmRemove?.fileName}</b> will no longer count towards verification. It stays in your submission
          history for auditing.
          {confirmRemove?.status === 'VERIFIED' && ' Removing an approved required document puts your verification on hold.'}
        </p>
      </Modal>
    </Card>
  );
}

function DocumentRow({ doc, onView, onDownload, onReplace, onRemove }) {
  const meta = DOC_STATUS[doc.status] || DOC_STATUS.PENDING;
  const expiringSoon = doc.status === 'VERIFIED' && doc.daysToExpiry != null && doc.daysToExpiry <= 30;
  return (
    <div className="mt-3 rounded-xl bg-slate-50 px-3 py-2.5 text-sm dark:bg-slate-800/60">
      <div className="flex flex-wrap items-center gap-2">
        <FileText className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
        <span className="min-w-0 flex-1 truncate font-medium text-slate-800 dark:text-slate-100" title={doc.fileName}>{doc.fileName}</span>
        <Badge tone={meta.tone}>{meta.label}</Badge>
      </div>
      <p className="mt-1 text-xs text-slate-500">
        {formatBytes(doc.fileSize)} · Uploaded {formatDateTime(doc.uploadedAt)}
        {doc.issueDate && <> · Issued {formatDate(doc.issueDate)}</>}
        {doc.expiryDate && <> · Expires {formatDate(doc.expiryDate)}</>}
        {doc.reviewedAt && <> · Reviewed {formatDateTime(doc.reviewedAt)}</>}
      </p>
      {doc.reviewRemarks && (
        <p className={`mt-1 text-xs ${doc.status === 'REJECTED' ? 'font-medium text-brand-700 dark:text-red-300' : 'text-slate-600 dark:text-slate-300'}`}>
          Administrator remarks: {doc.reviewRemarks}
        </p>
      )}
      {expiringSoon && (
        <p className="mt-1 text-xs font-medium text-amber-700 dark:text-amber-300">
          Expires in {doc.daysToExpiry} day{doc.daysToExpiry === 1 ? '' : 's'} — upload the renewed document early.
        </p>
      )}
      <div className="mt-2 flex flex-wrap gap-3 text-xs font-semibold">
        <button type="button" onClick={onView} className="inline-flex items-center gap-1 text-ocean-600 hover:underline" aria-label={`Preview ${doc.fileName}`}>
          <Eye className="h-3.5 w-3.5" aria-hidden="true" /> Preview
        </button>
        <button type="button" onClick={onDownload} className="inline-flex items-center gap-1 text-ocean-600 hover:underline" aria-label={`Download ${doc.fileName}`}>
          <Download className="h-3.5 w-3.5" aria-hidden="true" /> Download
        </button>
        <button type="button" onClick={onReplace} className="inline-flex items-center gap-1 text-slate-700 hover:underline dark:text-slate-200" aria-label={`Replace ${doc.fileName}`}>
          <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" /> Replace
        </button>
        <button type="button" onClick={onRemove} className="inline-flex items-center gap-1 text-brand-600 hover:underline" aria-label={`Remove ${doc.fileName}`}>
          <Trash2 className="h-3.5 w-3.5" aria-hidden="true" /> Remove
        </button>
      </div>
    </div>
  );
}
