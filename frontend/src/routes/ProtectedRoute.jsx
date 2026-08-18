import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { selectIsAuthenticated } from '../features/auth/authSlice';

/**
 * Gate for authenticated areas. Unauthenticated users are bounced to /login,
 * remembering where they wanted to go so we can return them after sign-in.
 */
export default function ProtectedRoute() {
  const isAuthed = useSelector(selectIsAuthenticated);
  const location = useLocation();

  if (!isAuthed) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
