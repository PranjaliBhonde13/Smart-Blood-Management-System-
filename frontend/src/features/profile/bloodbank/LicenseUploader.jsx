import { useEffect, useId, useRef, useState } from 'react';
import { UploadCloud, FileText, Image as ImageIcon, X, Eye, RefreshCw } from 'lucide-react';
import Button from '../../../components/ui/Button';
import { LICENSE_ACCEPT, LICENSE_MAX_BYTES, formatBytes, validateFile } from './bloodBankForm';

const EXTS = ['pdf', 'jpg', 'jpeg', 'png'];

/**
 * License certificate picker: drag-and-drop zone plus a Browse button, client
 * validation (type + size; the server re-checks both plus file signature),
 * preview, upload progress and remove/replace. The file is only staged here —
 * it is uploaded when the profile is saved.
 *
 * Props: saved (server doc metadata or null), staged (File|null), onStage(File|null),
 * onViewSaved(), progress (0–100|null while uploading), uploadError, error.
 */
export default function LicenseUploader({
  saved, staged, onStage, onViewSaved, progress, uploadError, error,
  noun = 'license certificate', guidance = 'Upload a clear scan of the full certificate showing the license number and validity.',
}) {
  const inputRef = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const [localError, setLocalError] = useState('');
  const [previewUrl, setPreviewUrl] = useState(null);
  const hintId = useId();
  const errorId = useId();

  // Object URL for previewing the staged file; revoked when it changes.
  useEffect(() => {
    if (!staged) { setPreviewUrl(null); return undefined; }
    const url = URL.createObjectURL(staged);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [staged]);

  const pick = (file) => {
    const msg = validateFile(file, { exts: EXTS, maxBytes: LICENSE_MAX_BYTES, label: noun });
    setLocalError(msg);
    if (!msg) onStage(file);
  };

  const openPicker = () => inputRef.current?.click();
  const uploading = progress != null;
  const shownError = localError || uploadError || error;
  const isImage = staged && staged.type.startsWith('image/');

  return (
    <div>
      <input
        ref={inputRef}
        type="file"
        accept={LICENSE_ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => { if (e.target.files?.[0]) pick(e.target.files[0]); e.target.value = ''; }}
      />

      {!staged && (
        <div
          role="button"
          tabIndex={0}
          aria-label={`Upload ${noun}. Drop a file here or press Enter to browse.`}
          aria-describedby={`${hintId}${shownError ? ` ${errorId}` : ''}`}
          onClick={openPicker}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); } }}
          onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            if (e.dataTransfer.files?.length > 1) { setLocalError('Drop a single file'); return; }
            if (e.dataTransfer.files?.[0]) pick(e.dataTransfer.files[0]);
          }}
          className={
            'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-4 py-8 text-center transition ' +
            'focus:outline-none focus-visible:ring-4 focus-visible:ring-brand-500/25 ' +
            (dragOver
              ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/20'
              : shownError
                ? 'border-brand-300 bg-white dark:border-brand-800 dark:bg-slate-900'
                : 'border-slate-300 bg-slate-50 hover:border-brand-400 hover:bg-brand-50/40 dark:border-slate-700 dark:bg-slate-900/60')
          }
        >
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-white text-brand-600 shadow-soft dark:bg-slate-800">
            <UploadCloud className="h-6 w-6" aria-hidden="true" />
          </span>
          <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">
            {saved ? `Drop a new file to replace the current ${noun}` : `Drag and drop your ${noun}`}
          </p>
          <p className="text-xs text-slate-500">or</p>
          <Button type="button" size="sm" variant="secondary" tabIndex={-1}
                  onClick={(e) => { e.stopPropagation(); openPicker(); }}>
            Browse files
          </Button>
        </div>
      )}

      {staged && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-start gap-3">
            {isImage && previewUrl ? (
              <img src={previewUrl} alt={`Preview of the selected ${noun}`}
                   className="h-16 w-16 shrink-0 rounded-xl border border-slate-200 object-cover dark:border-slate-700" />
            ) : (
              <span className="grid h-16 w-16 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-900/30">
                <FileText className="h-7 w-7" aria-hidden="true" />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-slate-900 dark:text-white" title={staged.name}>{staged.name}</p>
              <p className="text-xs text-slate-500">
                {formatBytes(staged.size)} · {uploading ? 'Uploading…' : 'Ready — uploads when you save'}
              </p>
              {previewUrl && !uploading && (
                <a href={previewUrl} target="_blank" rel="noreferrer"
                   className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-ocean-600 hover:underline">
                  <Eye className="h-3.5 w-3.5" aria-hidden="true" /> Preview
                </a>
              )}
            </div>
            {!uploading && (
              <div className="flex shrink-0 gap-1">
                <button type="button" onClick={openPicker} aria-label="Choose a different file"
                        className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:ring-2 focus-visible:ring-brand-500 dark:hover:bg-slate-800">
                  <RefreshCw className="h-4 w-4" />
                </button>
                <button type="button" onClick={() => { onStage(null); setLocalError(''); }} aria-label="Remove selected file"
                        className="rounded-lg p-2 text-slate-500 hover:bg-red-50 hover:text-brand-600 focus-visible:ring-2 focus-visible:ring-brand-500 dark:hover:bg-red-900/20">
                  <X className="h-4 w-4" />
                </button>
              </div>
            )}
          </div>
          {uploading && (
            <div className="mt-3" role="progressbar" aria-label={`${noun} upload progress`}
                 aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
              <div className="mb-1 flex justify-between text-xs font-medium text-slate-500">
                <span>Uploading</span><span>{progress}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div className="h-full rounded-full bg-grad-brand transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}
        </div>
      )}

      <p id={hintId} className="mt-2 text-xs text-slate-500 dark:text-slate-400">
        Accepted: PDF, JPG, PNG · Max {formatBytes(LICENSE_MAX_BYTES)} · {guidance}
        {' '}Documents are stored privately and visible only to you and platform administrators.
      </p>
      {shownError && <p id={errorId} role="alert" className="mt-1.5 text-xs font-medium text-brand-600">{shownError}</p>}

      {saved && (
        <div className="mt-3 flex flex-wrap items-center gap-3 rounded-xl bg-slate-50 px-3 py-2.5 text-sm dark:bg-slate-800/60">
          {saved.type?.startsWith('image/')
            ? <ImageIcon className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            : <FileText className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />}
          <span className="min-w-0 flex-1 truncate">
            <span className="text-slate-500">{staged ? 'Currently on file: ' : 'On file: '}</span>
            <span className="font-medium text-slate-800 dark:text-slate-100">{saved.name}</span>
            {saved.size != null && <span className="text-slate-500"> · {formatBytes(saved.size)}</span>}
          </span>
          <button type="button" onClick={onViewSaved}
                  className="inline-flex items-center gap-1 font-semibold text-ocean-600 hover:underline focus-visible:ring-2 focus-visible:ring-ocean-500">
            <Eye className="h-4 w-4" aria-hidden="true" /> View
          </button>
        </div>
      )}
    </div>
  );
}
