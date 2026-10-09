import { apiSlice } from '../../../app/apiSlice';

/**
 * Patient profile, verification documents and the admin review endpoints.
 * Everything reads/writes the server; verification fields only ever come back
 * from the API. Uses the shared 'User' tag so saving or uploading refreshes the
 * profile everywhere (including the New Request page's blood-group auto-fill).
 */
export const patientApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    myPatientProfile: builder.query({
      query: () => '/patients/me',
      providesTags: ['User'],
    }),
    savePatientProfile: builder.mutation({
      query: (body) => ({ url: '/patients/me', method: 'PUT', body }),
      invalidatesTags: ['User'],
    }),
    myPatientDocuments: builder.query({
      query: () => '/patients/me/documents',
      providesTags: ['User'],
    }),
    hospitalOptions: builder.query({
      query: () => '/blood-requests/hospital-options',
    }),
    // ----- admin -----
    patientVerificationQueue: builder.query({
      query: () => '/admin/patient-verifications',
      providesTags: ['User'],
    }),
    patientVerificationDetail: builder.query({
      query: (patientId) => `/admin/patient-verifications/patients/${patientId}`,
      providesTags: ['User'],
    }),
    reviewPatientDocument: builder.mutation({
      query: ({ documentId, ...body }) => ({
        url: `/admin/patient-verifications/documents/${documentId}/review`, method: 'PATCH', body,
      }),
      invalidatesTags: ['User'],
    }),
  }),
});

export const {
  useMyPatientProfileQuery,
  useSavePatientProfileMutation,
  useMyPatientDocumentsQuery,
  useHospitalOptionsQuery,
  usePatientVerificationQueueQuery,
  usePatientVerificationDetailQuery,
  useReviewPatientDocumentMutation,
} = patientApi;
