package com.example.irrigacion.service;

import com.example.irrigacion.config.MqttProperties;
import com.example.irrigacion.exception.MqttPublishException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttGateway {
    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final Mqtt5BlockingClient client;
    private final MqttProperties props;
    private final ObjectMapper objectMapper;

    public MqttGateway(Mqtt5BlockingClient client, MqttProperties props, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica un payload en un topic con QoS 1.
     */
    public void publish(String topic, byte[] payload) {
        try {
            client.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(payload)
                    .send();
        } catch (Exception e) {
            log.error("Error publicando MQTT en topic: {}", topic, e);
            throw new MqttPublishException("Error al publicar mensaje MQTT", e);
        }
    }

    /**
     * Conveniencia para comandos manuales ON/OFF.
     * Usa ObjectMapper para serialización segura en vez de concatenación de strings.
     */
    public void publishCommand(String nodeId, int zona, String accion, Integer duracionSeg) {
        String topic = String.format("riego/%s/cmd/zona/%d", nodeId, zona);
        
        try {
            // Usar ObjectMapper para serialización segura
            var payload = objectMapper.createObjectNode();
            payload.put("accion", accion);
            if (duracionSeg != null) {
                payload.put("duracion", duracionSeg);
            }
            
            byte[] json = objectMapper.writeValueAsBytes(payload);
            publish(topic, json);
            
            // Log sanitizado (sin exponer nodeId completo)
            String sanitizedNodeId = (nodeId != null && nodeId.length() > 8) 
                ? nodeId.substring(0, 8) + "..." 
                : "***";
            log.info("MQTT cmd publicado - nodeId={} zona={} accion={}", 
                sanitizedNodeId, zona, accion);
        } catch (JsonProcessingException e) {
            log.error("Error serializando comando MQTT", e);
            throw new MqttPublishException("Error al serializar comando", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            client.disconnect();
            log.info("MQTT desconectado");
        } catch (Exception e) {
            log.warn("Error al desconectar MQTT", e);
        }
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }
}
