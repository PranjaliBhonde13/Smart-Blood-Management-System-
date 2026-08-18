import { apiSlice } from '../../app/apiSlice';

/**
 * Donation endpoints (docs/04-API.md §7), keyed by request id. Mutations
 * invalidate 'Request', 'Donation', and 'Stock' so every affected list (queues,
 * history, inventory) refreshes after a fulfilment.
 */
export const donationsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    acceptRequest: builder.mutation({
      query: (requestId) => ({ url: `/donations/request/${requestId}/accept`, method: 'POST' }),
      invalidatesTags: ['Request', 'Donation'],
    }),
    rejectRequest: builder.mutation({
      query: (requestId) => ({ url: `/donations/request/${requestId}/reject`, method: 'POST' }),
      invalidatesTags: ['Request'],
    }),
    completeDonation: builder.mutation({
      query: (requestId) => ({ url: `/donations/request/${requestId}/complete`, method: 'PATCH' }),
      invalidatesTags: ['Request', 'Donation'],
    }),
    fulfillFromBank: builder.mutation({
      query: (requestId) => ({ url: `/donations/request/${requestId}/fulfill-from-bank`, method: 'POST' }),
      invalidatesTags: ['Request', 'Donation', 'Stock'],
    }),
    myDonations: builder.query({
      query: () => '/donations/mine',
      providesTags: ['Donation'],
    }),
  }),
});

export const {
  useAcceptRequestMutation,
  useRejectRequestMutation,
  useCompleteDonationMutation,
  useFulfillFromBankMutation,
  useMyDonationsQuery,
} = donationsApi;
