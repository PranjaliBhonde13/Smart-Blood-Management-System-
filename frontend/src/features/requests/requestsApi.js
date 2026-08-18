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
  useRequestQueueQuery,
  useOpenRequestsQuery,
  useVerifyRequestMutation,
  useUpdateRequestStatusMutation,
  useCancelRequestMutation,
  useUploadReportMutation,
} = requestsApi;
