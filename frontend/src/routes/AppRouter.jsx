import { lazy, Suspense } from 'react';
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
const DashboardHome = lazy(() => import('../features/dashboard/DashboardHome'));
const ProfilePage = lazy(() => import('../features/profile/ProfilePage'));
const ChangePasswordPage = lazy(() => import('../features/auth/pages/ChangePasswordPage'));
const BloodBankProfilePage = lazy(() => import('../features/profile/bloodbank/BloodBankProfilePage'));
const CreateRequestPage = lazy(() => import('../features/requests/pages/CreateRequestPage'));
const MyRequestsPage = lazy(() => import('../features/requests/pages/MyRequestsPage'));
const RequestQueuePage = lazy(() => import('../features/requests/pages/RequestQueuePage'));
const NearbySearchPage = lazy(() => import('../features/geo/NearbySearchPage'));
const StockPage = lazy(() => import('../features/inventory/StockPage'));
const OpenRequestsPage = lazy(() => import('../features/donations/pages/OpenRequestsPage'));
const DonationHistoryPage = lazy(() => import('../features/donations/pages/DonationHistoryPage'));
const BankRequestsPage = lazy(() => import('../features/donations/pages/BankRequestsPage'));
const AdminDashboardPage = lazy(() => import('../features/admin/pages/AdminDashboardPage'));
const UsersPage = lazy(() => import('../features/admin/pages/UsersPage'));
const VerificationsPage = lazy(() => import('../features/admin/pages/VerificationsPage'));
const HospitalReviewPage = lazy(() => import('../features/admin/pages/HospitalReviewPage'));
const PatientVerificationsPage = lazy(() => import('../features/admin/pages/PatientVerificationsPage'));
const AuditLogPage = lazy(() => import('../features/admin/pages/AuditLogPage'));
const ReportsPage = lazy(() => import('../features/admin/pages/ReportsPage'));
const AdminRequestsPage = lazy(() => import('../features/admin/pages/AdminRequestsPage'));
const UserDetailPage = lazy(() => import('../features/admin/pages/UserDetailPage'));
const InventoryPage = lazy(() => import('../features/admin/pages/InventoryPage'));
const AnalyticsPage = lazy(() => import('../features/admin/pages/AnalyticsPage'));
const BroadcastPage = lazy(() => import('../features/admin/pages/BroadcastPage'));

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
      { path: 'profile', element: <BloodBankProfilePage /> },
      { path: 'inventory', element: <StockPage /> },
      { path: 'requests', element: <BankRequestsPage /> },
    ],
  },
  {
    path: 'admin', role: ROLES.ADMIN,
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: 'requests', element: <AdminRequestsPage /> },
      { path: 'users', element: <UsersPage /> },
      { path: 'users/:id', element: <UserDetailPage /> },
      { path: 'inventory', element: <InventoryPage /> },
      { path: 'analytics', element: <AnalyticsPage /> },
      { path: 'broadcast', element: <BroadcastPage /> },
      { path: 'verifications', element: <VerificationsPage /> },
      { path: 'verifications/hospitals/:id', element: <HospitalReviewPage /> },
      { path: 'patient-verifications', element: <PatientVerificationsPage /> },
      { path: 'audit-logs', element: <AuditLogPage /> },
      { path: 'reports', element: <ReportsPage /> },
    ],
  },
];

/** Shown while a lazily loaded page chunk downloads. */
function RouteFallback() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-brand-600" />
    </div>
  );
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
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
            {/* Account settings shared by every role. */}
            <Route path="/account/password" element={<ChangePasswordPage />} />
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
      </Suspense>
    </BrowserRouter>
  );
}
