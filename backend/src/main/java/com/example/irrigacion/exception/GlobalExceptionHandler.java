package com.example.irrigacion.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejo global de excepciones para prevenir exposición de stack traces
 * y proporcionar respuestas de error consistentes.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validación fallida: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Errores de validación: {}", errors);
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Error de validación en los datos proporcionados",
            HttpStatus.BAD_REQUEST.value(),
            OffsetDateTime.now(),
            errors
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.info("Recurso no encontrado: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "FORBIDDEN",
            ex.getMessage(),
            HttpStatus.FORBIDDEN.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        log.warn("Rate limit excedido: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "RATE_LIMIT_EXCEEDED",
            ex.getMessage(),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }
    
    @ExceptionHandler(MqttPublishException.class)
    public ResponseEntity<ErrorResponse> handleMqttPublish(MqttPublishException ex) {
        log.error("Error publicando MQTT: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "MQTT_ERROR",
            "Error al comunicarse con el broker MQTT",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log completo del error para debugging interno
        log.error("Error no controlado", ex);
        
        // NO exponer detalles internos al cliente
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_ERROR",
            "Error interno del servidor. Por favor contacte al administrador.",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
