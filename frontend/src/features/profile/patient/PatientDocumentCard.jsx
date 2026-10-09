import { useState } from 'react';
import { FileText, Eye, Send, History } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import Button from '../../../components/ui/Button';
import Select from '../../../components/ui/Select';
import { formatDateTime } from '../../../lib/format';
import { formatBytes } from '../bloodbank/bloodBankForm';
import LicenseUploader from '../bloodbank/LicenseUploader';
import { TRACK_META } from './patientProfileModel';

/**
 * One verification track (identity or blood group): current document with its
 * server-reported status and admin remarks, a secure "View" (fetched with the
 * token, never a public URL), previous versions, and an uploader to submit or
 * replace the document. Uploads go straight to the server on "Submit".
 */
export default function PatientDocumentCard({
  id, title, description, track, typeOptions, history = [], onUpload, onView,
  disabledReason, guidance, children,
}) {
  const doc = track?.document;
  const status = track?.status || 'NOT_SUBMITTED';
  const meta = TRACK_META[status];
  const [type, setType] = useState('');
  const [staged, setStaged] = useState(null);
  const [progress, setProgress] = useState(null);
  const [uploadError, setUploadError] = useState('');
  const [typeError, setTypeError] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const previous = history.filter((h) => !h.current);
  const needsAction = status === 'REJECTED' || status === 'ACTION_REQUIRED';

  const submit = async () => {
    setUploadError('');
    if (!type) { setTypeError('Choose the document type'); return; }
    setTypeError('');
    setProgress(0);
    try {
      await onUpload(type, staged, setProgress);
      setStaged(null);
      setType('');
    } catch (e) {
      setUploadError(e.message || 'Upload failed');
    } finally {
      setProgress(null);
    }
  };

  return (
    <div className="rounded-2xl border border-slate-200 p-4 dark:border-slate-700" role="group" aria-labelledby={`${id}-title`}>
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 id={`${id}-title`} className="font-semibold text-slate-900 dark:text-white">{title}</h3>
          <p className="mt-0.5 text-sm text-slate-500">{description}</p>
        </div>
        <Badge tone={meta.tone} dot>{meta.label}</Badge>
      </div>

      {children}

      {doc && (
        <div className="mb-3 rounded-xl bg-slate-50 p-3 text-sm dark:bg-slate-800/60">
          <div className="flex flex-wrap items-center gap-2">
            <FileText className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            <span className="font-medium text-slate-800 dark:text-slate-100">{doc.documentTypeLabel}</span>
            <span className="min-w-0 truncate text-slate-500" title={doc.fileName}>· {doc.fileName} · {formatBytes(doc.fileSize)}</span>
            <button type="button" onClick={() => onView(doc.id)}
                    className="ml-auto inline-flex items-center gap-1 font-semibold text-ocean-600 hover:underline focus-visible:ring-2 focus-visible:ring-ocean-500">
              <Eye className="h-4 w-4" aria-hidden="true" /> View
            </button>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Uploaded {formatDateTime(doc.uploadedAt)}
            {doc.reviewedAt && ` · Reviewed ${formatDateTime(doc.reviewedAt)}`}
          </p>
          {needsAction && (
            <div className="mt-2 rounded-lg border border-red-200 bg-white px-3 py-2 dark:border-red-900/60 dark:bg-slate-900" role="alert">
              <p className="text-xs font-semibold text-brand-700 dark:text-red-300">Administrator remarks</p>
              <p className="mt-0.5 whitespace-pre-line text-sm text-slate-700 dark:text-slate-200">{doc.reviewRemarks || 'No remarks were provided.'}</p>
              <p className="mt-1 text-xs text-slate-500">Upload a corrected document below to resubmit.</p>
            </div>
          )}
        </div>
      )}

      {disabledReason ? (
        <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-100">{disabledReason}</p>
      ) : (
        <div className="space-y-3">
          <Select id={`${id}-type`} label={doc ? 'Replace with document type' : 'Document type'} value={type}
                  onChange={(e) => { setType(e.target.value); setTypeError(''); }} error={typeError}
                  disabled={progress != null} aria-label={doc ? 'Replace with document type' : 'Document type'}>
            <option value="">Select…</option>
            {typeOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
          <LicenseUploader noun={title.toLowerCase()} guidance={guidance} saved={null} staged={staged}
                           onStage={(f) => { setStaged(f); setUploadError(''); }} onViewSaved={() => {}}
                           progress={progress} uploadError={uploadError} />
          {staged && (
            <Button type="button" size="sm" onClick={submit} loading={progress != null} leftIcon={<Send className="h-4 w-4" />}>
              {progress != null ? `Uploading ${progress}%` : doc ? 'Submit replacement for verification' : 'Submit for verification'}
            </Button>
          )}
        </div>
      )}

      {previous.length > 0 && (
        <div className="mt-3">
          <button type="button" onClick={() => setShowHistory((s) => !s)} aria-expanded={showHistory}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-slate-800 dark:hover:text-slate-200">
            <History className="h-3.5 w-3.5" aria-hidden="true" /> {showHistory ? 'Hide' : 'Show'} previous uploads ({previous.length})
          </button>
          {showHistory && (
            <ul className="mt-2 space-y-1 text-xs text-slate-500">
              {previous.map((h) => (
                <li key={h.id} className="flex flex-wrap items-center gap-2">
                  <span>{formatDateTime(h.uploadedAt)}</span>·<span>{h.documentTypeLabel}</span>·
                  <span>{TRACK_META[h.status]?.label || h.status}</span>
                  <button type="button" className="font-semibold text-ocean-600 hover:underline" onClick={() => onView(h.id)}>View</button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
