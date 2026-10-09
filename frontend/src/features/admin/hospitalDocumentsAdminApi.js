import { apiSlice } from '../../app/apiSlice';

/**
 * Admin review of hospital documents. Every decision is made server-side by the
 * authenticated admin; mutations invalidate the verification queue ('User') too.
 */
export const hospitalDocumentsAdminApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    hospitalReview: builder.query({
      query: (hospitalId) => `/admin/hospitals/${hospitalId}/documents`,
      // 'User' too: hospital approve/reject (adminApi) invalidates it.
      providesTags: ['HospitalDocs', 'User'],
    }),
    approveHospitalDocument: builder.mutation({
      query: ({ id, remarks }) => ({ url: `/admin/hospital-documents/${id}/approve`, method: 'PATCH', body: { remarks: remarks || null } }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
    rejectHospitalDocument: builder.mutation({
      query: ({ id, remarks, requestCorrection }) => ({
        url: `/admin/hospital-documents/${id}/reject`, method: 'PATCH', body: { remarks, requestCorrection },
      }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
    requestHospitalDocument: builder.mutation({
      query: ({ hospitalId, ...body }) => ({ url: `/admin/hospitals/${hospitalId}/document-requests`, method: 'POST', body }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
    cancelHospitalDocumentRequest: builder.mutation({
      query: (id) => ({ url: `/admin/hospital-document-requests/${id}/cancel`, method: 'PATCH' }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
    hospitalDocumentRequirements: builder.query({
      query: () => '/admin/hospital-document-requirements',
      providesTags: ['HospitalDocs'],
    }),
    updateHospitalDocumentRequirement: builder.mutation({
      query: ({ id, ...body }) => ({ url: `/admin/hospital-document-requirements/${id}`, method: 'PATCH', body }),
      invalidatesTags: ['HospitalDocs', 'User'],
    }),
  }),
});

export const {
  useHospitalReviewQuery,
  useApproveHospitalDocumentMutation,
  useRejectHospitalDocumentMutation,
  useRequestHospitalDocumentMutation,
  useCancelHospitalDocumentRequestMutation,
  useHospitalDocumentRequirementsQuery,
  useUpdateHospitalDocumentRequirementMutation,
} = hospitalDocumentsAdminApi;
