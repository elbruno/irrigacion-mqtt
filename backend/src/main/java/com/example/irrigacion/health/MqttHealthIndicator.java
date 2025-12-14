package com.example.irrigacion.health;

import com.example.irrigacion.config.MqttProperties;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Health check para verificar la conexión MQTT.
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttHealthIndicator implements HealthIndicator {
    
    private final Mqtt5BlockingClient mqttClient;
    private final MqttProperties mqttProperties;
    
    public MqttHealthIndicator(Optional<Mqtt5BlockingClient> mqttClient, MqttProperties mqttProperties) {
        this.mqttClient = mqttClient.orElse(null);
        this.mqttProperties = mqttProperties;
    }
    
    @Override
    public Health health() {
        if (mqttClient == null) {
            return Health.down()
                .withDetail("mqtt", "Cliente MQTT no disponible")
                .build();
        }
        
        try {
            if (mqttClient.getState().isConnected()) {
                return Health.up()
                    .withDetail("mqtt", "Connected")
                    .withDetail("broker", mqttProperties.getHost() + ":" + mqttProperties.getPort())
                    .withDetail("tls", mqttProperties.isTls())
                    .build();
            } else {
                return Health.down()
                    .withDetail("mqtt", "Disconnected")
                    .withDetail("broker", mqttProperties.getHost() + ":" + mqttProperties.getPort())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("mqtt", "Error verificando estado")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
