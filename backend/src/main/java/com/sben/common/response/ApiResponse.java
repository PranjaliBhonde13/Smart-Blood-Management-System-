package com.sben.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Uniform success envelope returned by every endpoint.
 *
 * <p>Keeps the API contract consistent so the React client can rely on a single
 * response shape (see docs/04-API.md). Errors use {@code ApiError} instead.
 *
 * @param <T> payload type
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    /** Convenience factory for a successful response. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }
}
