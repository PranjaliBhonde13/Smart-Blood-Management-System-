import { apiSlice } from '../../app/apiSlice';

/**
 * Generic profile endpoints. The `resource` argument selects the role's REST
 * path (patients | donors | hospitals | blood-banks), so one pair of endpoints
 * serves all roles instead of four near-identical copies (DRY).
 */
export const profileApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getMyProfile: builder.query({
      query: (resource) => `/${resource}/me`,
      providesTags: ['User'],
    }),
    upsertMyProfile: builder.mutation({
      query: ({ resource, body }) => ({ url: `/${resource}/me`, method: 'PUT', body }),
      invalidatesTags: ['User'],
    }),
  }),
});

export const { useGetMyProfileQuery, useUpsertMyProfileMutation } = profileApi;
