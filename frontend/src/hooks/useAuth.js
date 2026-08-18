import { useDispatch, useSelector } from 'react-redux';
import { selectCurrentUser, selectIsAuthenticated, logout } from '../features/auth/authSlice';

/**
 * Convenience hook exposing the current user, auth status, and a logout action.
 * Centralises auth access so components don't reach into the store directly.
 */
export function useAuth() {
  const dispatch = useDispatch();
  const user = useSelector(selectCurrentUser);
  const isAuthenticated = useSelector(selectIsAuthenticated);
  return {
    user,
    isAuthenticated,
    role: user?.role,
    signOut: () => dispatch(logout()),
  };
}
