import { apiSlice } from '../../app/apiSlice';

/** Blood bank inventory endpoints (docs/04-API.md §5). */
export const stockApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    myStock: builder.query({
      query: () => '/blood-stock/mine',
      providesTags: ['Stock'],
    }),
    upsertStock: builder.mutation({
      query: (body) => ({ url: '/blood-stock', method: 'PUT', body }),
      invalidatesTags: ['Stock'],
    }),
  }),
});

export const { useMyStockQuery, useUpsertStockMutation } = stockApi;
