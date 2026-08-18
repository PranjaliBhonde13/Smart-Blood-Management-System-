import { Navigate, Outlet } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { selectCurrentUser } from '../features/auth/authSlice';

/**
 * Restricts a route subtree to specific roles (RBAC on the client — the server
 * still enforces authorization on every request). Wrong-role users are sent to
 * a 403 page.
 *
 * @param {{allow: string[]}} props roles permitted to view the subtree
 */
export default function RoleGuard({ allow }) {
  const user = useSelector(selectCurrentUser);
  if (!user) return <Navigate to="/login" replace />;
  if (!allow.includes(user.role)) return <Navigate to="/forbidden" replace />;
  return <Outlet />;
}
