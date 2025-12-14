package com.example.irrigacion.service;

import com.example.irrigacion.dto.AgendaRequest;
import com.example.irrigacion.dto.AgendaResponse;
import com.example.irrigacion.model.Agenda;
import com.example.irrigacion.model.AgendaVersion;
import com.example.irrigacion.repository.AgendaRepository;
import com.example.irrigacion.repository.AgendaVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgendaService {
    private static final Logger log = LoggerFactory.getLogger(AgendaService.class);

    private final AgendaRepository agendaRepository;
    private final AgendaVersionRepository versionRepository;
    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;

    public AgendaService(AgendaRepository agendaRepository,
                         AgendaVersionRepository versionRepository,
                         Optional<MqttGateway> mqttGateway,
                         ObjectMapper objectMapper) {
        this.agendaRepository = agendaRepository;
        this.versionRepository = versionRepository;
        this.mqttGateway = mqttGateway.orElse(null);
        this.objectMapper = objectMapper;
    }

    public List<AgendaResponse> list(UUID nodeId) {
        return agendaRepository.findByNodeId(nodeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AgendaResponse upsert(AgendaRequest request) {
        validateNoOverlap(request);
        int newVersion = nextVersion(request.getNodeId());
        Agenda agenda = agendaRepository.findById(request.getId()).orElseGet(Agenda::new);
        agenda.setId(request.getId());
        agenda.setNodeId(request.getNodeId());
        agenda.setZona(request.getZona());
        agenda.setDiasSemana(request.getDiasSemana());
        agenda.setHoraInicio(LocalTime.parse(request.getHoraInicio()));
        agenda.setDuracionMin(request.getDuracionMin());
        agenda.setActiva(request.isActiva());
        agenda.setVersion(newVersion);
        agenda.setUpdatedAt(OffsetDateTime.now());

        agendaRepository.save(agenda);
        publishSync(request.getNodeId(), newVersion);
        return toResponse(agenda);
    }

    @Transactional
    public void delete(UUID nodeId, UUID agendaId) {
        Agenda agenda = agendaRepository.findByNodeIdAndId(nodeId, agendaId)
                .orElseThrow(() -> new IllegalArgumentException("Agenda no encontrada"));
        agendaRepository.delete(agenda);
        int newVersion = nextVersion(nodeId);
        publishSync(nodeId, newVersion);
    }

    private void validateNoOverlap(AgendaRequest req) {
        List<Agenda> agendas = agendaRepository.findActiveByNodeAndZona(req.getNodeId(), req.getZona());
        LocalTime start = LocalTime.parse(req.getHoraInicio());
        LocalTime end = start.plusMinutes(req.getDuracionMin());
        
        // Validar que no cruza medianoche (simplificación para MVP)
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                "La programación cruza medianoche. Debe dividirse en dos agendas separadas."
            );
        }
        
        // Validar overlaps con otras agendas activas
        for (Agenda a : agendas) {
            if (a.getId().equals(req.getId())) {
                continue; // misma agenda, se actualiza
            }
            LocalTime aStart = a.getHoraInicio();
            LocalTime aEnd = aStart.plusMinutes(a.getDuracionMin());
            boolean overlapDays = a.getDiasSemana().stream().anyMatch(req.getDiasSemana()::contains);
            boolean overlapTime = start.isBefore(aEnd) && end.isAfter(aStart);
            if (overlapDays && overlapTime) {
                throw new IllegalArgumentException(
                    String.format("Solape de agenda en zona %d. Conflicto con agenda %s (%s - %s)",
                        req.getZona(), a.getId(), aStart, aEnd)
                );
            }
        }
    }

    private int nextVersion(UUID nodeId) {
        AgendaVersion version = versionRepository.findByNodeId(nodeId).orElseGet(() -> {
            AgendaVersion v = new AgendaVersion();
            v.setNodeId(nodeId);
            v.setVersion(0);
            v.setUpdatedAt(OffsetDateTime.now());
            return v;
        });
        version.setVersion(version.getVersion() + 1);
        version.setUpdatedAt(OffsetDateTime.now());
        versionRepository.save(version);
        return version.getVersion();
    }

    private void publishSync(UUID nodeId, int version) {
        if (mqttGateway == null || !mqttGateway.isEnabled()) {
            log.info("MQTT deshabilitado; no se publica sync");
            return;
        }
        try {
            List<Agenda> agendas = agendaRepository.findByNodeId(nodeId);
            var payload = objectMapper.createObjectNode();
            payload.put("version", version);
            payload.put("updatedAt", OffsetDateTime.now().toString());
            var arr = payload.putArray("agendas");
            agendas.forEach(a -> {
                var n = arr.addObject();
                n.put("id", a.getId().toString());
                n.put("zona", a.getZona());
                var dias = n.putArray("diasSemana");
                a.getDiasSemana().forEach(dias::add);
                n.put("horaInicio", a.getHoraInicio().toString());
                n.put("duracionMin", a.getDuracionMin());
                n.put("activa", a.isActiva());
            });
            String topic = "riego/" + nodeId + "/agenda/sync";
            mqttGateway.publish(topic, objectMapper.writeValueAsBytes(payload));
            log.info("Publicado sync version={} nodeId={} agendas={}", version, nodeId, agendas.size());
        } catch (Exception e) {
            throw new RuntimeException("Error publicando sync MQTT", e);
        }
    }

    private AgendaResponse toResponse(Agenda a) {
        AgendaResponse r = new AgendaResponse();
        r.setId(a.getId());
        r.setNodeId(a.getNodeId());
        r.setZona(a.getZona());
        r.setDiasSemana(a.getDiasSemana());
        r.setHoraInicio(a.getHoraInicio().toString());
        r.setDuracionMin(a.getDuracionMin());
        r.setActiva(a.isActiva());
        r.setVersion(a.getVersion());
        r.setUpdatedAt(a.getUpdatedAt());
        return r;
    }
}
