/**
 * Normalises RTK Query errors into a message + per-field map so pages can show
 * a top-level alert and inline field errors consistently. Matches the backend
 * ApiError envelope (docs/04-API.md §1).
 */
export function parseApiError(error) {
  const data = error?.data;
  return {
    message: data?.message || 'Something went wrong. Please try again.',
    fieldErrors: data?.fieldErrors || {},
  };
}
