import { BrowserRouter, Routes, Route } from 'react-router-dom';
import LandingPage from '../pages/LandingPage';
import Placeholder from '../pages/Placeholder';
import ProtectedRoute from './ProtectedRoute';
import RoleGuard from './RoleGuard';
import { ROLES } from '../lib/constants';
import LoginPage from '../features/auth/pages/LoginPage';
import RegisterPage from '../features/auth/pages/RegisterPage';
import VerifyOtpPage from '../features/auth/pages/VerifyOtpPage';
import ForgotPasswordPage from '../features/auth/pages/ForgotPasswordPage';
import ResetPasswordPage from '../features/auth/pages/ResetPasswordPage';
import DashboardLayout from '../components/layout/DashboardLayout';
import DashboardHome from '../features/dashboard/DashboardHome';
import ProfilePage from '../features/profile/ProfilePage';
import CreateRequestPage from '../features/requests/pages/CreateRequestPage';
import MyRequestsPage from '../features/requests/pages/MyRequestsPage';
import RequestQueuePage from '../features/requests/pages/RequestQueuePage';
import NearbySearchPage from '../features/geo/NearbySearchPage';
import StockPage from '../features/inventory/StockPage';
import OpenRequestsPage from '../features/donations/pages/OpenRequestsPage';
import DonationHistoryPage from '../features/donations/pages/DonationHistoryPage';
import BankRequestsPage from '../features/donations/pages/BankRequestsPage';
import AdminDashboardPage from '../features/admin/pages/AdminDashboardPage';
import UsersPage from '../features/admin/pages/UsersPage';
import VerificationsPage from '../features/admin/pages/VerificationsPage';
import AuditLogPage from '../features/admin/pages/AuditLogPage';
import ReportsPage from '../features/admin/pages/ReportsPage';

/**
 * Route map. Public auth screens, then the authenticated app:
 *   ProtectedRoute → DashboardLayout (shared shell) → RoleGuard per role.
 * Each role block declares its own child routes; every role gets dashboard +
 * (where applicable) profile, plus feature routes added module by module.
 */
const ROLE_BLOCKS = [
  {
    path: 'patient', role: ROLES.PATIENT,
    children: [
      { index: true, element: <DashboardHome /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'requests', element: <MyRequestsPage /> },
      { path: 'requests/new', element: <CreateRequestPage /> },
      { path: 'find', element: <NearbySearchPage /> },
    ],
  },
  {
    path: 'donor', role: ROLES.DONOR,
    children: [
      { index: true, element: <DashboardHome /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'open-requests', element: <OpenRequestsPage /> },
      { path: 'donations', element: <DonationHistoryPage /> },
    ],
  },
  {
    path: 'hospital', role: ROLES.HOSPITAL,
    children: [
      { index: true, element: <DashboardHome /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'queue', element: <RequestQueuePage /> },
      { path: 'find', element: <NearbySearchPage /> },
    ],
  },
  {
    path: 'blood-bank', role: ROLES.BLOOD_BANK,
    children: [
      { index: true, element: <DashboardHome /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'inventory', element: <StockPage /> },
      { path: 'requests', element: <BankRequestsPage /> },
    ],
  },
  {
    path: 'admin', role: ROLES.ADMIN,
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: 'users', element: <UsersPage /> },
      { path: 'verifications', element: <VerificationsPage /> },
      { path: 'audit-logs', element: <AuditLogPage /> },
      { path: 'reports', element: <ReportsPage /> },
    ],
  },
];

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-otp" element={<VerifyOtpPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/forbidden" element={<Placeholder title="403 — Forbidden" />} />

        {/* Authenticated app */}
        <Route element={<ProtectedRoute />}>
          <Route element={<DashboardLayout />}>
            {ROLE_BLOCKS.map(({ path, role, children }) => (
              <Route key={path} element={<RoleGuard allow={[role]} />}>
                <Route path={path}>
                  {children.map((c) =>
                    c.index
                      ? <Route key="index" index element={c.element} />
                      : <Route key={c.path} path={c.path} element={c.element} />
                  )}
                </Route>
              </Route>
            ))}
          </Route>
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Placeholder title="404 — Not Found" />} />
      </Routes>
    </BrowserRouter>
  );
}
