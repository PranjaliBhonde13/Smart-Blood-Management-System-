import { useEffect, useId, useRef, useState } from 'react';
import { UploadCloud, FileText, X, Eye, RefreshCw } from 'lucide-react';
import Button from '../../../components/ui/Button';
import { DOC_ACCEPT, formatBytes, validateDocFile } from './documentStatus';

/**
 * Drag-and-drop file picker with a Browse button, client pre-validation (the
 * server re-checks type, real content and size), a local preview, and upload
 * progress. Holds one staged file; the parent performs the upload.
 */
export default function DocumentDropzone({ file, onFile, maxBytes, progress, error, disabled }) {
  const inputRef = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const [localError, setLocalError] = useState('');
  const [previewUrl, setPreviewUrl] = useState(null);
  const hintId = useId();
  const errorId = useId();

  useEffect(() => {
    if (!file) { setPreviewUrl(null); return undefined; }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const pick = (f) => {
    const msg = validateDocFile(f, maxBytes);
    setLocalError(msg);
    if (!msg) onFile(f);
  };
  const openPicker = () => { if (!disabled) inputRef.current?.click(); };
  const uploading = progress != null;
  const shownError = localError || error;
  const isImage = file && file.type.startsWith('image/');

  return (
    <div>
      <input ref={inputRef} type="file" accept={DOC_ACCEPT} className="sr-only" tabIndex={-1} aria-hidden="true"
             onChange={(e) => { if (e.target.files?.[0]) pick(e.target.files[0]); e.target.value = ''; }} />

      {!file ? (
        <div
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-disabled={disabled || undefined}
          aria-label="Upload document. Drop a file here or press Enter to browse."
          aria-describedby={`${hintId}${shownError ? ` ${errorId}` : ''}`}
          onClick={openPicker}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); } }}
          onDragOver={(e) => { e.preventDefault(); if (!disabled) setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            if (disabled) return;
            if (e.dataTransfer.files?.length > 1) { setLocalError('Drop a single file'); return; }
            if (e.dataTransfer.files?.[0]) pick(e.dataTransfer.files[0]);
          }}
          className={
            'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-4 py-7 text-center transition ' +
            'focus:outline-none focus-visible:ring-4 focus-visible:ring-brand-500/25 ' +
            (disabled ? 'cursor-not-allowed opacity-60 ' : '') +
            (dragOver
              ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/20'
              : shownError
                ? 'border-brand-300 bg-white dark:border-brand-800 dark:bg-slate-900'
                : 'border-slate-300 bg-slate-50 hover:border-brand-400 hover:bg-brand-50/40 dark:border-slate-700 dark:bg-slate-900/60')
          }
        >
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-white text-brand-600 shadow-soft dark:bg-slate-800">
            <UploadCloud className="h-6 w-6" aria-hidden="true" />
          </span>
          <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Drag and drop your document here</p>
          <p className="text-xs text-slate-500">or</p>
          <Button type="button" size="sm" variant="secondary" tabIndex={-1} disabled={disabled}
                  onClick={(e) => { e.stopPropagation(); openPicker(); }}>
            Browse files
          </Button>
        </div>
      ) : (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-start gap-3">
            {isImage && previewUrl ? (
              <img src={previewUrl} alt="Preview of the selected document"
                   className="h-14 w-14 shrink-0 rounded-xl border border-slate-200 object-cover dark:border-slate-700" />
            ) : (
              <span className="grid h-14 w-14 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-900/30">
                <FileText className="h-6 w-6" aria-hidden="true" />
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-slate-900 dark:text-white" title={file.name}>{file.name}</p>
              <p className="text-xs text-slate-500">{formatBytes(file.size)} · {uploading ? 'Uploading…' : 'Ready to upload'}</p>
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
                <button type="button" onClick={() => { onFile(null); setLocalError(''); }} aria-label="Remove selected file"
                        className="rounded-lg p-2 text-slate-500 hover:bg-red-50 hover:text-brand-600 focus-visible:ring-2 focus-visible:ring-brand-500 dark:hover:bg-red-900/20">
                  <X className="h-4 w-4" />
                </button>
              </div>
            )}
          </div>
          {uploading && (
            <div className="mt-3" role="progressbar" aria-label="Upload progress"
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
        Accepted: PDF, JPG, PNG · Max {formatBytes(maxBytes)}. Documents are stored privately and visible only to
        your hospital and platform administrators.
      </p>
      {shownError && <p id={errorId} role="alert" className="mt-1.5 text-xs font-medium text-brand-600">{shownError}</p>}
    </div>
  );
}
