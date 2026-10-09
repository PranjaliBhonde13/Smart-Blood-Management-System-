import { useEffect, useState } from 'react';

/** Plain-language messages for the browser's GeolocationPositionError codes. */
const ERROR_MESSAGES = {
  1: 'Location access is blocked. Allow location for this site in your browser settings, or enter your address and coordinates manually.',
  2: 'Your location could not be determined right now. Check that location services are on, or enter your details manually.',
  3: 'Getting your location took too long. Try again, or enter your details manually.',
};

/**
 * Thin wrapper over the browser Geolocation API. Returns a `request` function
 * that resolves the user's coordinates plus loading/error state, and the
 * current permission (`'granted' | 'prompt' | 'denied' | 'unknown'`) where the
 * Permissions API is available. Used to auto-fill location on profiles and for
 * nearby search.
 */
export function useGeolocation() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [permission, setPermission] = useState('unknown');

  useEffect(() => {
    let status;
    const onChange = () => setPermission(status.state);
    navigator.permissions?.query({ name: 'geolocation' })
      .then((s) => { status = s; setPermission(s.state); s.addEventListener('change', onChange); })
      .catch(() => { /* Permissions API unsupported: stay 'unknown' */ });
    return () => status?.removeEventListener('change', onChange);
  }, []);

  const request = () =>
    new Promise((resolve, reject) => {
      if (!window.isSecureContext) {
        setError('Location is only available over a secure (https) connection. Enter your details manually.');
        reject(new Error('insecure'));
        return;
      }
      if (!navigator.geolocation) {
        setError('Geolocation is not supported by your browser.');
        reject(new Error('unsupported'));
        return;
      }
      setLoading(true);
      setError('');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setLoading(false);
          resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy });
        },
        (err) => {
          setLoading(false);
          setError(ERROR_MESSAGES[err.code] || err.message || 'Unable to get your location.');
          reject(err);
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    });

  return { request, loading, error, permission, clearError: () => setError('') };
}
