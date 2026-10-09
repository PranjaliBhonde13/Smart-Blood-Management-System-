import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '../lib/constants';
import { logout, tokensRefreshed } from '../features/auth/authSlice';

/**
 * Central RTK Query API slice. Every feature injects its endpoints into this
 * one slice (injectEndpoints) so we get a single cache, one middleware, and
 * automatic re-fetching via tag invalidation.
 *
 * The JWT access token is attached from Redux state. Access tokens are short
 * lived (15 min): on a 401 the base query exchanges the refresh token once and
 * retries; if that fails the session is over and the user is signed out.
 */
const rawBaseQuery = fetchBaseQuery({
  baseUrl: API_BASE_URL,
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth?.accessToken;
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return headers;
  },
});

// Refresh tokens rotate (single use), so concurrent 401s must share one refresh.
let refreshing = null;

async function refreshSession(api, extraOptions) {
  const refreshToken = api.getState().auth?.refreshToken;
  if (!refreshToken) return false;
  const res = await rawBaseQuery(
    { url: '/auth/refresh', method: 'POST', body: { refreshToken } },
    api,
    extraOptions,
  );
  if (res.data?.data?.accessToken) {
    api.dispatch(tokensRefreshed(res.data.data));
    return true;
  }
  return false;
}

const baseQueryWithReauth = async (args, api, extraOptions) => {
  let result = await rawBaseQuery(args, api, extraOptions);
  const url = typeof args === 'string' ? args : args.url;
  const isAuthCall = url.startsWith('/auth/login') || url.startsWith('/auth/refresh');

  if (result.error?.status === 401 && !isAuthCall && api.getState().auth?.accessToken) {
    refreshing = refreshing || refreshSession(api, extraOptions).finally(() => { refreshing = null; });
    if (await refreshing) {
      result = await rawBaseQuery(args, api, extraOptions);
    } else {
      api.dispatch(logout());
      api.dispatch(apiSlice.util.resetApiState());
    }
  }
  return result;
};

export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  // Cache tags used by feature endpoints to invalidate on mutations.
  tagTypes: ['User', 'Request', 'Donation', 'Stock', 'Notification', 'HospitalDocs', 'Email'],
  endpoints: () => ({}),
});
