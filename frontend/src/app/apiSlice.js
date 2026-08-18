import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '../lib/constants';

/**
 * Central RTK Query API slice. Every feature injects its endpoints into this
 * one slice (injectEndpoints) so we get a single cache, one middleware, and
 * automatic re-fetching via tag invalidation.
 *
 * The JWT access token (added in Module 1) is attached from Redux state here.
 */
export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: (headers, { getState }) => {
      const token = getState().auth?.accessToken;
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  // Cache tags used by feature endpoints to invalidate on mutations.
  tagTypes: ['User', 'Request', 'Donation', 'Stock', 'Notification'],
  endpoints: () => ({}),
});
