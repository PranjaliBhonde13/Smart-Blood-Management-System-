package com.sben.common.exception;

/**
 * Thrown when a requested entity does not exist (or is soft-deleted).
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String entity, Object id) {
        super("%s not found with id: %s".formatted(entity, id));
    }
}
