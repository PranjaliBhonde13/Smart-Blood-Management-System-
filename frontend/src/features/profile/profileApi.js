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
      // Show the saved profile at once (also replaces a first-time 404) while the refetch runs.
      async onQueryStarted({ resource }, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(profileApi.util.upsertQueryData('getMyProfile', resource, data));
        } catch { /* the caller handles the error */ }
      },
      invalidatesTags: ['User'],
    }),
    /** Optional donor profile photo (PNG/JPG, stored privately; read with lib/privateFiles). */
    uploadDonorPhoto: builder.mutation({
      query: (file) => {
        const body = new FormData();
        body.append('file', file);
        return { url: '/donors/me/photo', method: 'POST', body };
      },
      invalidatesTags: ['User'],
    }),
    removeDonorPhoto: builder.mutation({
      query: () => ({ url: '/donors/me/photo', method: 'DELETE' }),
      invalidatesTags: ['User'],
    }),
  }),
});

export const {
  useGetMyProfileQuery,
  useUpsertMyProfileMutation,
  useUploadDonorPhotoMutation,
  useRemoveDonorPhotoMutation,
} = profileApi;
