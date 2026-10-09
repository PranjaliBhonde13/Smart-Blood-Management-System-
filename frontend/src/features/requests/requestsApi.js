import { apiSlice } from '../../app/apiSlice';

/**
 * Blood request endpoints (docs/04-API.md §4). Mutations invalidate the
 * 'Request' tag so lists refresh automatically after create/verify/status/cancel.
 */
export const requestsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    createRequest: builder.mutation({
      query: (body) => ({ url: '/blood-requests', method: 'POST', body }),
      invalidatesTags: ['Request'],
    }),
    myRequests: builder.query({
      query: (page = 0) => `/blood-requests/mine?page=${page}&size=20&sort=requestDate,desc`,
      providesTags: ['Request'],
    }),
    /** Up to 200 most recent own requests — for dashboard counts. */
    myRequestsSummary: builder.query({
      query: () => '/blood-requests/mine?page=0&size=200&sort=requestDate,desc',
      providesTags: ['Request'],
    }),
    requestQueue: builder.query({
      query: ({ status = '', page = 0 } = {}) =>
        `/blood-requests?page=${page}&size=20&sort=requestDate,desc${status ? `&status=${status}` : ''}`,
      providesTags: ['Request'],
    }),
    openRequests: builder.query({
      query: (page = 0) => `/blood-requests/open?page=${page}&size=20&sort=requestDate,desc`,
      providesTags: ['Request'],
    }),
    verifyRequest: builder.mutation({
      query: (id) => ({ url: `/blood-requests/${id}/verify`, method: 'PATCH' }),
      invalidatesTags: ['Request'],
    }),
    updateRequestStatus: builder.mutation({
      query: ({ id, status }) => ({ url: `/blood-requests/${id}/status`, method: 'PATCH', body: { status } }),
      invalidatesTags: ['Request'],
    }),
    /** Hospital/admin: confirm (same group) or correct (new group + reason) the requested group. Audited. */
    confirmBloodGroup: builder.mutation({
      query: ({ id, bloodGroup, reason }) => ({ url: `/blood-requests/${id}/blood-group`, method: 'PATCH', body: { bloodGroup, reason } }),
      invalidatesTags: ['Request'],
    }),
    cancelRequest: builder.mutation({
      query: (id) => ({ url: `/blood-requests/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Request'],
    }),
    uploadReport: builder.mutation({
      query: ({ id, file }) => {
        const form = new FormData();
        form.append('file', file);
        return { url: `/blood-requests/${id}/report`, method: 'POST', body: form };
      },
      invalidatesTags: ['Request'],
    }),
  }),
});

export const {
  useCreateRequestMutation,
  useMyRequestsQuery,
  useMyRequestsSummaryQuery,
  useRequestQueueQuery,
  useOpenRequestsQuery,
  useVerifyRequestMutation,
  useUpdateRequestStatusMutation,
  useCancelRequestMutation,
  useConfirmBloodGroupMutation,
  useUploadReportMutation,
} = requestsApi;
