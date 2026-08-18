import { apiSlice } from '../../app/apiSlice';

/**
 * Admin control-plane endpoints (docs/04-API.md §9). Verification/enable
 * mutations invalidate 'User' so dashboards and lists refresh after an action.
 */
export const adminApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    dashboard: builder.query({
      query: () => '/admin/dashboard',
      providesTags: ['User'],
    }),
    adminUsers: builder.query({
      query: ({ role = '', page = 0 } = {}) =>
        `/admin/users?page=${page}&size=20${role ? `&role=${role}` : ''}`,
      providesTags: ['User'],
    }),
    setUserEnabled: builder.mutation({
      query: ({ id, enabled }) => ({ url: `/admin/users/${id}/${enabled ? 'enable' : 'disable'}`, method: 'PATCH' }),
      invalidatesTags: ['User'],
    }),
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
    auditLogs: builder.query({
      query: (page = 0) => `/admin/audit-logs?page=${page}&size=30`,
    }),
  }),
});

export const {
  useDashboardQuery,
  useAdminUsersQuery,
  useSetUserEnabledMutation,
  usePendingVerificationsQuery,
  useVerifyHospitalMutation,
  useVerifyBloodBankMutation,
  useAuditLogsQuery,
} = adminApi;
