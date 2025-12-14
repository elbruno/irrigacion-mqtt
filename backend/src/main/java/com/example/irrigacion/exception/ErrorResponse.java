package com.example.irrigacion.exception;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Respuesta de error estandarizada para la API.
 */
public record ErrorResponse(
    String code,
    String message,
    int status,
    OffsetDateTime timestamp,
    Map<String, String> details
) {
    public ErrorResponse(String code, String message, int status, OffsetDateTime timestamp) {
        this(code, message, status, timestamp, null);
    }
}
