import { apiSlice } from '../../../app/apiSlice';

/**
 * The signed-in hospital's documents. Uploads go through uploadWithProgress
 * (XHR, for progress) and then invalidate these tags; see HospitalDocumentsSection.
 * 'User' is included so the profile (and its verification status) refetches too.
 */
export const hospitalDocumentsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    myHospitalDocuments: builder.query({
      query: () => '/hospitals/me/documents',
      providesTags: ['HospitalDocs', 'User'],
    }),
    removeHospitalDocument: builder.mutation({
      query: (id) => ({ url: `/hospitals/me/documents/${id}`, method: 'DELETE' }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
  }),
});

export const { useMyHospitalDocumentsQuery, useRemoveHospitalDocumentMutation } = hospitalDocumentsApi;
