import { useEffect, useRef } from 'react';
import { useGoogleMaps } from '../../hooks/useGoogleMaps';

/**
 * Renders search results on a Google Map when a key is configured; otherwise
 * shows a clear placeholder (the results still appear as lists beneath it).
 *
 * @param {{center:{lat,lng}, markers:Array<{lat,lng,label,color}>}} props
 */
export default function MapView({ center, markers = [] }) {
  const { status } = useGoogleMaps();
  const ref = useRef(null);
  const mapRef = useRef(null);
  const markerObjs = useRef([]);

  useEffect(() => {
    if (status !== 'ready' || !ref.current || !center) return;
    const g = window.google;
    if (!mapRef.current) {
      mapRef.current = new g.maps.Map(ref.current, { center, zoom: 12, mapId: 'SBEN_MAP' });
    } else {
      mapRef.current.setCenter(center);
    }
    // Reset markers each render.
    markerObjs.current.forEach((m) => m.setMap(null));
    markerObjs.current = [];
    // Requester (center) marker.
    markerObjs.current.push(new g.maps.Marker({
      position: center, map: mapRef.current, title: 'You',
      icon: { path: g.maps.SymbolPath.CIRCLE, scale: 8, fillColor: '#2563eb', fillOpacity: 1, strokeWeight: 2, strokeColor: '#fff' },
    }));
    markers.forEach((m) => {
      markerObjs.current.push(new g.maps.Marker({
        position: { lat: m.lat, lng: m.lng }, map: mapRef.current, title: m.label,
        icon: { path: g.maps.SymbolPath.CIRCLE, scale: 7, fillColor: m.color || '#dc2626', fillOpacity: 1, strokeWeight: 1, strokeColor: '#fff' },
      }));
    });
  }, [status, center, markers]);

  if (status === 'disabled' || status === 'error') {
    return (
      <div className="flex h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 text-center text-sm text-slate-500 dark:border-slate-700">
        <div className="mb-1 text-2xl">🗺️</div>
        {status === 'error' ? 'Map failed to load.' : 'Map preview'}
        <span className="mt-1 max-w-xs text-xs">
          Add <code>VITE_GOOGLE_MAPS_API_KEY</code> to <code>frontend/.env</code> to enable the live map.
          Results are listed below regardless.
        </span>
      </div>
    );
  }

  return (
    <div className="relative h-64 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800">
      {status === 'loading' && (
        <div className="absolute inset-0 animate-pulse bg-slate-100 dark:bg-slate-800" />
      )}
      <div ref={ref} className="h-full w-full" />
    </div>
  );
}
