package com.sben.common.exception;

import java.util.Map;

/**
 * Thrown when a service-level rule (one bean validation cannot express, such as
 * a field that depends on another) rejects specific fields. Rendered exactly
 * like a bean validation failure: 400 with {@code fieldErrors}.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
