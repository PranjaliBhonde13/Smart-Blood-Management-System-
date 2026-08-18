import { apiSlice } from '../../app/apiSlice';

/** Reporting summaries (docs/04-API.md §10). CSV export is fetched directly. */
export const reportsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    requestsSummary: builder.query({ query: () => '/reports/requests/summary' }),
    donationsSummary: builder.query({ query: () => '/reports/donations/summary' }),
    inventorySummary: builder.query({ query: () => '/reports/inventory/summary' }),
  }),
});

export const {
  useRequestsSummaryQuery,
  useDonationsSummaryQuery,
  useInventorySummaryQuery,
} = reportsApi;
