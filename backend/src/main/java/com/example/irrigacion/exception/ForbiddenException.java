package com.example.irrigacion.exception;

/**
 * Excepción lanzada cuando un usuario no tiene permisos para acceder a un recurso.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
