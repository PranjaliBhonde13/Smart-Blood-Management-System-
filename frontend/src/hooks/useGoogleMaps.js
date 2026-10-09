import { useEffect, useState } from 'react';

/**
 * Loads the Google Maps JS API once, if a key is configured via
 * VITE_GOOGLE_MAPS_API_KEY. Returns { status } where status is:
 *   'disabled'  – no key set (UI falls back to a list-only view)
 *   'loading'   – script injecting
 *   'ready'     – window.google.maps available
 *   'error'     – failed to load
 *
 * Keeping this optional means the whole app runs with zero external keys during
 * development and lights up the map automatically once a key is provided.
 */
let loadPromise = null;

export function useGoogleMaps() {
  const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  const [status, setStatus] = useState(() => {
    if (!key) return 'disabled';
    if (typeof window !== 'undefined' && window.google?.maps) return 'ready';
    return 'loading';
  });

  useEffect(() => {
    if (!key || status === 'ready' || status === 'disabled') return;

    if (!loadPromise) {
      loadPromise = new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&v=weekly&libraries=places`;
        script.async = true;
        script.defer = true;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
      });
    }
    loadPromise.then(() => setStatus('ready')).catch(() => setStatus('error'));
  }, [key, status]);

  return { status };
}
