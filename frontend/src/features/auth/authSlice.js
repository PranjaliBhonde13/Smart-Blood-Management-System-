import { createSlice } from '@reduxjs/toolkit';

/**
 * Auth state: the current user and JWT tokens. "Remember me" keeps the session
 * in localStorage (survives closing the browser); otherwise it lives in
 * sessionStorage and ends with the tab.
 */
const KEY = 'sben_auth';

function readPersisted() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || sessionStorage.getItem(KEY) || 'null');
  } catch {
    return null;
  }
}

function persist(state) {
  try {
    const data = JSON.stringify(state);
    if (state.remember) {
      localStorage.setItem(KEY, data);
      sessionStorage.removeItem(KEY);
    } else {
      sessionStorage.setItem(KEY, data);
      localStorage.removeItem(KEY);
    }
  } catch { /* storage unavailable: session stays in memory */ }
}

function clearPersisted() {
  try {
    localStorage.removeItem(KEY);
    sessionStorage.removeItem(KEY);
  } catch { /* ignore */ }
}

const initialState = readPersisted() || {
  user: null,
  accessToken: null,
  refreshToken: null,
  remember: true,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, { payload }) => {
      state.user = payload.user;
      state.accessToken = payload.accessToken;
      state.refreshToken = payload.refreshToken;
      if (payload.remember !== undefined) state.remember = payload.remember;
      persist(state);
    },
    /** New token pair from /auth/refresh; user and remember choice unchanged. */
    tokensRefreshed: (state, { payload }) => {
      state.accessToken = payload.accessToken;
      state.refreshToken = payload.refreshToken;
      if (payload.user) state.user = payload.user;
      persist(state);
    },
    /** Profile edits to account fields (e.g. full name) saved on the server. */
    userUpdated: (state, { payload }) => {
      if (!state.user) return;
      state.user = { ...state.user, ...payload };
      persist(state);
    },
    logout: (state) => {
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
      clearPersisted();
    },
  },
});

export const { setCredentials, tokensRefreshed, userUpdated, logout } = authSlice.actions;
export const selectCurrentUser = (s) => s.auth.user;
export const selectIsAuthenticated = (s) => Boolean(s.auth.accessToken);
export default authSlice.reducer;
