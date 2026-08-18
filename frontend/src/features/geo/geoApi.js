import { apiSlice } from '../../app/apiSlice';

/**
 * Geo/matching endpoints (docs/04-API.md §6). Exposed as lazy queries so the
 * search page fires them on demand when the user submits the form.
 */
export const geoApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    nearbyDonors: builder.query({
      query: ({ bloodGroup, lat, lng, radiusKm }) =>
        `/geo/nearby/donors?bloodGroup=${encodeURIComponent(bloodGroup)}&lat=${lat}&lng=${lng}&radiusKm=${radiusKm}`,
    }),
    nearbyBanks: builder.query({
      query: ({ bloodGroup, lat, lng, radiusKm }) =>
        `/geo/nearby/blood-banks?bloodGroup=${encodeURIComponent(bloodGroup)}&lat=${lat}&lng=${lng}&radiusKm=${radiusKm}`,
    }),
  }),
});

export const { useLazyNearbyDonorsQuery, useLazyNearbyBanksQuery } = geoApi;
