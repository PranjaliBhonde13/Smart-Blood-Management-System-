import { apiSlice } from '../../app/apiSlice';

/**
 * Notification endpoints (docs/04-API.md §8). The unread-count query polls so
 * the bell badge stays live; mutations invalidate the tag to refresh instantly.
 */
export const notificationsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    notifications: builder.query({
      query: (page = 0) => `/notifications?page=${page}&size=20`,
      providesTags: ['Notification'],
    }),
    unreadCount: builder.query({
      query: () => '/notifications/unread-count',
      providesTags: ['Notification'],
    }),
    markRead: builder.mutation({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'PATCH' }),
      invalidatesTags: ['Notification'],
    }),
    markAllRead: builder.mutation({
      query: () => ({ url: '/notifications/read-all', method: 'PATCH' }),
      invalidatesTags: ['Notification'],
    }),
  }),
});

export const {
  useNotificationsQuery,
  useUnreadCountQuery,
  useMarkReadMutation,
  useMarkAllReadMutation,
} = notificationsApi;
