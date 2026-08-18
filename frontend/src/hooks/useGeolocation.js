import { useState } from 'react';

/**
 * Thin wrapper over the browser Geolocation API. Returns a `request` function
 * that resolves the user's coordinates plus loading/error state. Used to
 * auto-fill location on profiles and for nearby search in Module 4.
 */
export function useGeolocation() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const request = () =>
    new Promise((resolve, reject) => {
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
          resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        },
        (err) => {
          setLoading(false);
          setError(err.message || 'Unable to get your location.');
          reject(err);
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    });

  return { request, loading, error };
}
