import { createSlice } from '@reduxjs/toolkit';

/**
 * Auth state: the current user and JWT tokens. Persisted to localStorage so a
 * page refresh keeps the session. Login/logout reducers are wired to the auth
 * API endpoints in Module 1.
 */
const persisted = JSON.parse(localStorage.getItem('sben_auth') || 'null');

const initialState = persisted || {
  user: null,
  accessToken: null,
  refreshToken: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, { payload }) => {
      state.user = payload.user;
      state.accessToken = payload.accessToken;
      state.refreshToken = payload.refreshToken;
      localStorage.setItem('sben_auth', JSON.stringify(state));
    },
    logout: (state) => {
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
      localStorage.removeItem('sben_auth');
    },
  },
});

export const { setCredentials, logout } = authSlice.actions;
export const selectCurrentUser = (s) => s.auth.user;
export const selectIsAuthenticated = (s) => Boolean(s.auth.accessToken);
export default authSlice.reducer;
