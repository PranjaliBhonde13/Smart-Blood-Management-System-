import { useEffect, useRef, useState } from 'react';
import { useGoogleMaps } from '../../hooks/useGoogleMaps';

function placeCoordinates(place) {
  const location = place.location;
  if (!location) return null;

  const lat = Number(typeof location.lat === 'function' ? location.lat() : location.lat);
  const lng = Number(typeof location.lng === 'function' ? location.lng() : location.lng);
  return Number.isFinite(lat) && Number.isFinite(lng) ? { lat, lng } : null;
}

function distanceKm(from, to) {
  const toRadians = (value) => (value * Math.PI) / 180;
  const latDelta = toRadians(to.lat - from.lat);
  const lngDelta = toRadians(to.lng - from.lng);
  const a = Math.sin(latDelta / 2) ** 2
    + Math.cos(toRadians(from.lat)) * Math.cos(toRadians(to.lat)) * Math.sin(lngDelta / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function placeName(place) {
  return typeof place.displayName === 'string'
    ? place.displayName
    : place.displayName?.text || 'Blood bank';
}

/**
 * Renders search results on a Google Map when a key is configured; otherwise
 * shows a clear placeholder (the results still appear as lists beneath it).
 *
 * @param {{center:{lat,lng}, markers:Array<{lat,lng,label,color}>}} props
 */
export default function MapView({ center, markers = [], radiusKm = 15 }) {
  const { status } = useGoogleMaps();
  const ref = useRef(null);
  const mapRef = useRef(null);
  const markerObjs = useRef([]);
  const placeMarkerObjs = useRef([]);
  const [placesStatus, setPlacesStatus] = useState('idle');
  const centerLat = center?.lat;
  const centerLng = center?.lng;

  useEffect(() => {
    if (status !== 'ready' || !ref.current || !center) return;
    const g = window.google;
    const validMarkers = markers.filter((marker) =>
      Number.isFinite(marker.lat)
      && Number.isFinite(marker.lng)
      && Math.abs(marker.lat) <= 90
      && Math.abs(marker.lng) <= 180,
    );
    if (!mapRef.current) {
      mapRef.current = new g.maps.Map(ref.current, { center, zoom: 12 });
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
    validMarkers.forEach((m) => {
      markerObjs.current.push(new g.maps.Marker({
        position: { lat: m.lat, lng: m.lng }, map: mapRef.current, title: m.label,
        icon: { path: g.maps.SymbolPath.CIRCLE, scale: 7, fillColor: m.color || '#dc2626', fillOpacity: 1, strokeWeight: 1, strokeColor: '#fff' },
      }));
    });

    if (validMarkers.length > 0) {
      const bounds = new g.maps.LatLngBounds();
      bounds.extend(center);
      validMarkers.forEach((marker) => bounds.extend({ lat: marker.lat, lng: marker.lng }));
      mapRef.current.fitBounds(bounds, 48);
    }
  }, [status, center, markers]);

  useEffect(() => {
    if (status !== 'ready' || !mapRef.current || !Number.isFinite(centerLat) || !Number.isFinite(centerLng)) return undefined;

    let cancelled = false;
    const g = window.google;
    const searchCenter = { lat: centerLat, lng: centerLng };
    placeMarkerObjs.current.forEach((marker) => marker.setMap(null));
    placeMarkerObjs.current = [];
    setPlacesStatus('loading');

    const searchGoogleBloodBanks = async () => {
      try {
        const { Place } = await g.maps.importLibrary('places');
        const { places = [] } = await Place.searchByText({
          textQuery: 'blood bank',
          fields: ['displayName', 'location'],
          locationBias: { center: searchCenter, radius: Math.min(Number(radiusKm) * 1000, 50000) },
          maxResultCount: 20,
        });

        if (cancelled) return;

        const realBanks = places
          .map((place) => ({ place, position: placeCoordinates(place) }))
          .filter(({ position }) => position && distanceKm(searchCenter, position) <= Number(radiusKm));
        const infoWindow = new g.maps.InfoWindow();

        realBanks.forEach(({ place, position }) => {
          const marker = new g.maps.Marker({
            position,
            map: mapRef.current,
            title: placeName(place),
            icon: { path: g.maps.SymbolPath.CIRCLE, scale: 8, fillColor: '#059669', fillOpacity: 1, strokeWeight: 2, strokeColor: '#ffffff' },
          });
          marker.addListener('click', () => {
            const content = document.createElement('div');
            content.textContent = placeName(place);
            infoWindow.setContent(content);
            infoWindow.open({ map: mapRef.current, anchor: marker });
          });
          placeMarkerObjs.current.push(marker);
        });

        setPlacesStatus(realBanks.length > 0 ? 'ready' : 'empty');
      } catch {
        if (!cancelled) setPlacesStatus('error');
      }
    };

    void searchGoogleBloodBanks();
    return () => {
      cancelled = true;
    };
  }, [status, centerLat, centerLng, radiusKm]);

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
      {placesStatus === 'loading' && (
        <div className="absolute bottom-3 left-3 rounded-md bg-white/95 px-2.5 py-1.5 text-xs font-medium text-slate-700 shadow-sm dark:bg-slate-900/95 dark:text-slate-200">
          Finding nearby blood banks...
        </div>
      )}
      {placesStatus === 'error' && (
        <div className="absolute bottom-3 left-3 rounded-md bg-amber-50 px-2.5 py-1.5 text-xs font-medium text-amber-800 shadow-sm dark:bg-amber-950/90 dark:text-amber-200">
          Nearby place search is unavailable.
        </div>
      )}
    </div>
  );
}
