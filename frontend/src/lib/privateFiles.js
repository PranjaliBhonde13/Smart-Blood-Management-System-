import { API_BASE_URL } from './constants';

/**
 * Helpers for the blood bank's private files. Uploads use XMLHttpRequest because
 * fetch cannot report upload progress; downloads are fetched with the bearer
 * token and exposed as short-lived object URLs (the files are never public).
 */

/**
 * POST one file as multipart/form-data and report progress (0–100).
 * `extraFields` are sent as additional form fields (empty values are skipped).
 * Resolves with the API envelope; rejects with { message, fieldErrors }.
 */
export function uploadWithProgress(path, file, token, onProgress, extraFields = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE_URL}${path}`);
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => {
      let body = {};
      try { body = JSON.parse(xhr.responseText || '{}'); } catch { /* non-JSON error page */ }
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.(100);
        resolve(body);
      } else if (xhr.status === 401) {
        reject({ message: 'Your session expired. Please save again.' });
      } else {
        reject({ message: body.message || `Upload failed (HTTP ${xhr.status})`, fieldErrors: body.fieldErrors || {} });
      }
    };
    xhr.onerror = () => reject({ message: 'Network error — check your connection and try again.' });
    xhr.onabort = () => reject({ message: 'Upload cancelled.' });
    const form = new FormData();
    form.append('file', file);
    Object.entries(extraFields).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') form.append(k, String(v));
    });
    xhr.send(form);
  });
}

/** Fetch a protected file and return an object URL (caller must revoke it). */
export async function fetchObjectUrl(path, token) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || 'Could not load the file');
  }
  return URL.createObjectURL(await res.blob());
}

/** Download a protected file under the given filename. */
export async function downloadPrivateFile(path, token, filename) {
  const url = await fetchObjectUrl(path, token);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || 'document';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** Open a protected file in a new tab (tab opened synchronously to avoid popup blockers). */
export async function openPrivateFile(path, token) {
  const tab = window.open('', '_blank');
  try {
    const url = await fetchObjectUrl(path, token);
    if (tab) tab.location.href = url;
    else window.location.assign(url);
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    tab?.close();
    throw e;
  }
}
