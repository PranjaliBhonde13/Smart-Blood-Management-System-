import { useDispatch, useSelector, useStore } from 'react-redux';
import { selectCurrentUser, selectIsAuthenticated, logout } from '../features/auth/authSlice';
import { apiSlice } from '../app/apiSlice';
import { API_BASE_URL } from '../lib/constants';

/**
 * Convenience hook exposing the current user, auth status, and a sign-out action.
 * Centralises auth access so components don't reach into the store directly.
 */
export function useAuth() {
  const dispatch = useDispatch();
  const store = useStore();
  const user = useSelector(selectCurrentUser);
  const isAuthenticated = useSelector(selectIsAuthenticated);

  /**
   * Revoke the session on the server (refresh + access tokens), then clear
   * local state and the API cache so the next user on this browser never sees
   * this user's cached data.
   */
  const signOut = () => {
    const token = store.getState().auth?.accessToken;
    if (token) {
      fetch(`${API_BASE_URL}/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => { /* offline: local sign-out still happens */ });
    }
    dispatch(logout());
    dispatch(apiSlice.util.resetApiState());
  };

  return { user, isAuthenticated, role: user?.role, signOut };
}
