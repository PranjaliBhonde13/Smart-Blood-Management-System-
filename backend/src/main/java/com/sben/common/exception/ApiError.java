package com.sben.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Uniform error envelope (see docs/04-API.md §1).
 * {@code fieldErrors} is present only for validation failures.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    @Builder.Default
    private final boolean success = false;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Map<String, String> fieldErrors;
}
