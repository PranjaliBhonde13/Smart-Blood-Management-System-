import { API_BASE_URL } from './constants';
import { notify } from './toast';

/**
 * Opens a request's medical report in a new tab. The file endpoint needs the
 * bearer token, so it is fetched as a blob and shown from an object URL.
 */
export async function openReport(requestId, token) {
  // Open the tab synchronously (inside the click) so popup blockers allow it.
  const tab = window.open('', '_blank');
  try {
    const res = await fetch(`${API_BASE_URL}/blood-requests/${requestId}/report`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || 'Could not open the report');
    }
    const url = URL.createObjectURL(await res.blob());
    if (tab) tab.location.href = url;
    else window.location.assign(url);
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    tab?.close();
    notify.error(e.message || 'Could not open the report');
  }
}
