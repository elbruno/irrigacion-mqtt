package com.example.irrigacion.exception;

/**
 * Excepción lanzada cuando falla la publicación de un mensaje MQTT.
 */
public class MqttPublishException extends RuntimeException {
    public MqttPublishException(String message) {
        super(message);
    }
    
    public MqttPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
