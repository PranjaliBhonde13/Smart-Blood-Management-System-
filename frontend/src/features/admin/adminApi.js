import { apiSlice } from '../../app/apiSlice';

/** Builds a query string from an object, skipping empty values. */
const qs = (params) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v);
  });
  const s = p.toString();
  return s ? `?${s}` : '';
};

/**
 * Admin control-plane endpoints (docs/04-API.md §9). Mutations invalidate the
 * tags their screens read, so lists and dashboards refresh after an action.
 */
export const adminApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    dashboard: builder.query({
      query: () => '/admin/dashboard',
      providesTags: ['User', 'Request'],
    }),

    // ----- users -----
    adminUsers: builder.query({
      query: ({ role = '', status = '', q = '', page = 0 } = {}) =>
        `/admin/users${qs({ page, size: 20, sort: 'createdAt,desc', role, status, q })}`,
      providesTags: ['User'],
    }),
    userDetail: builder.query({
      query: (id) => `/admin/users/${id}`,
      providesTags: ['User'],
    }),
    setUserEnabled: builder.mutation({
      query: ({ id, enabled, reason }) => ({
        url: `/admin/users/${id}/${enabled ? 'enable' : 'disable'}`,
        method: 'PATCH',
        ...(enabled ? {} : { body: { reason: reason || null } }),
      }),
      invalidatesTags: ['User'],
    }),
    forceLogout: builder.mutation({
      query: (id) => ({ url: `/admin/users/${id}/logout`, method: 'POST' }),
      invalidatesTags: ['User'],
    }),
    sendPasswordReset: builder.mutation({
      query: (id) => ({ url: `/admin/users/${id}/password-reset`, method: 'POST' }),
      invalidatesTags: ['User'],
    }),
    createAdmin: builder.mutation({
      query: (body) => ({ url: '/admin/admins', method: 'POST', body }),
      invalidatesTags: ['User'],
    }),

    // ----- verifications -----
    pendingVerifications: builder.query({
      query: () => '/admin/verifications',
      providesTags: ['User'],
    }),
    verifyHospital: builder.mutation({
      query: (id) => ({ url: `/admin/hospitals/${id}/verify`, method: 'PATCH' }),
      invalidatesTags: ['User'],
    }),
    verifyBloodBank: builder.mutation({
      query: (id) => ({ url: `/admin/blood-banks/${id}/verify`, method: 'PATCH' }),
      invalidatesTags: ['User'],
    }),
    rejectHospital: builder.mutation({
      query: ({ id, reason }) => ({ url: `/admin/hospitals/${id}/reject`, method: 'PATCH', body: { reason } }),
      invalidatesTags: ['User'],
    }),
    rejectBloodBank: builder.mutation({
      query: ({ id, reason }) => ({ url: `/admin/blood-banks/${id}/reject`, method: 'PATCH', body: { reason } }),
      invalidatesTags: ['User'],
    }),

    // ----- requests -----
    adminRequests: builder.query({
      query: ({ status = '', urgency = '', bloodGroup = '', from = '', to = '', page = 0 } = {}) =>
        `/admin/requests${qs({ page, size: 20, status, urgency, bloodGroup, from, to })}`,
      providesTags: ['Request'],
    }),
    stuckRequests: builder.query({
      query: () => '/admin/requests/stuck',
      providesTags: ['Request'],
    }),
    escalateRequest: builder.mutation({
      query: (id) => ({ url: `/admin/requests/${id}/escalate`, method: 'POST' }),
      invalidatesTags: ['Request'],
    }),

    // ----- insights -----
    networkInventory: builder.query({
      query: () => '/admin/inventory',
      providesTags: ['Stock'],
    }),
    analytics: builder.query({
      query: (days = 30) => `/admin/analytics?days=${days}`,
      providesTags: ['Request'],
    }),

    // ----- broadcast -----
    broadcast: builder.mutation({
      query: (body) => ({ url: '/admin/broadcast', method: 'POST', body }),
    }),

    // ----- audit -----
    auditLogs: builder.query({
      query: ({ page = 0, action = '', actorId = '', entityType = '', from = '', to = '' } = {}) =>
        `/admin/audit-logs${qs({ page, size: 30, action, actorId, entityType, from, to })}`,
      providesTags: ['User'],
    }),
    auditActions: builder.query({
      query: () => '/admin/audit-logs/actions',
      providesTags: ['User'],
    }),
  }),
});

export const {
  useDashboardQuery,
  useAdminUsersQuery,
  useUserDetailQuery,
  useSetUserEnabledMutation,
  useForceLogoutMutation,
  useSendPasswordResetMutation,
  useCreateAdminMutation,
  usePendingVerificationsQuery,
  useVerifyHospitalMutation,
  useVerifyBloodBankMutation,
  useRejectHospitalMutation,
  useRejectBloodBankMutation,
  useAdminRequestsQuery,
  useStuckRequestsQuery,
  useEscalateRequestMutation,
  useNetworkInventoryQuery,
  useAnalyticsQuery,
  useBroadcastMutation,
  useAuditLogsQuery,
  useAuditActionsQuery,
} = adminApi;
