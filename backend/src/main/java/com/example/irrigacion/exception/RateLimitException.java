package com.example.irrigacion.exception;

/**
 * Excepción lanzada cuando se excede el límite de requests permitidos.
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
