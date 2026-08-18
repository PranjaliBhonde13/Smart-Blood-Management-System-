package com.sben.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a business rule is violated (e.g. an invalid status transition,
 * duplicate resource, or ineligible donor). Carries the HTTP status to return.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
