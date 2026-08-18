import { configureStore } from '@reduxjs/toolkit';
import { apiSlice } from './apiSlice';
import authReducer from '../features/auth/authSlice';

/**
 * Redux store. Combines the RTK Query API slice (data/cache) with feature
 * slices (client state). The API middleware enables caching, invalidation,
 * and polling.
 */
export const store = configureStore({
  reducer: {
    [apiSlice.reducerPath]: apiSlice.reducer,
    auth: authReducer,
  },
  middleware: (getDefault) => getDefault().concat(apiSlice.middleware),
});
