package com.example.irrigacion.exception;

/**
 * Excepción lanzada cuando un recurso no es encontrado.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
