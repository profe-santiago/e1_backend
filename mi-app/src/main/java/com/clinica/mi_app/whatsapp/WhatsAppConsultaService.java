package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.dto.response.ConversacionResumenResponse;
import com.clinica.mi_app.dto.response.RecordatorioEstadoResponse;
import com.clinica.mi_app.dto.response.WhatsAppMensajeResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Lecturas del historial de WhatsApp, siempre acotadas a la organización del usuario. */
@Service
@Transactional(readOnly = true)
public class WhatsAppConsultaService {

    private static final Set<String> TIPOS_RECORDATORIO = Set.of("RECORDATORIO_48H", "RECORDATORIO_24H", "RECORDATORIO_MANUAL");
    private static final int MAX_CITAS_ESTADO = 200;

    private final WhatsAppMensajeRepository repo;
    private final CitaRepository citaRepo;
    private final NotificacionRepository notificacionRepo;

    public WhatsAppConsultaService(WhatsAppMensajeRepository repo, CitaRepository citaRepo,
                                   NotificacionRepository notificacionRepo) {
        this.repo = repo;
        this.citaRepo = citaRepo;
        this.notificacionRepo = notificacionRepo;
    }

    /** Historial por cita o por teléfono (uno de los dos es obligatorio). */
    public List<WhatsAppMensajeResponse> mensajes(UUID organizacionId, UUID citaId, String telefono) {
        List<WhatsAppMensaje> mensajes;
        if (citaId != null) {
            citaRepo.findById(citaId)
                    .filter(c -> c.getOrganizacion().getId().equals(organizacionId))
                    .orElseThrow(() -> new ResourceNotFoundException("Cita", citaId.toString()));
            mensajes = repo.findByOrganizacionIdAndCitaIdOrderByCreatedAtAsc(organizacionId, citaId);
        } else if (telefono != null && !telefono.isBlank()) {
            String tel = TelefonoMx.aDiezDigitos(telefono);
            if (tel == null) throw new IllegalArgumentException("Teléfono inválido: se esperan 10 dígitos");
            mensajes = repo.findByOrganizacionIdAndTelefonoOrderByCreatedAtAsc(organizacionId, tel);
        } else {
            throw new IllegalArgumentException("Indica citaId o teléfono");
        }
        return mensajes.stream().map(WhatsAppMensajeResponse::of).toList();
    }

    /** Lista de conversaciones (una por teléfono), la más reciente primero. */
    public List<ConversacionResumenResponse> resumen(UUID organizacionId) {
        Map<String, List<WhatsAppMensaje>> porTelefono = new LinkedHashMap<>();
        for (WhatsAppMensaje m : repo.findTop500ByOrganizacionIdOrderByCreatedAtDesc(organizacionId)) {
            porTelefono.computeIfAbsent(m.getTelefono(), k -> new ArrayList<>()).add(m);
        }
        List<ConversacionResumenResponse> resultado = new ArrayList<>();
        porTelefono.forEach((tel, mensajes) -> {
            WhatsAppMensaje ultimo = mensajes.get(0);
            Optional<WhatsAppMensaje> conCita = mensajes.stream().filter(x -> x.getCita() != null).findFirst();
            resultado.add(new ConversacionResumenResponse(tel,
                    conCita.map(x -> x.getCita().getPaciente().getNombre()).orElse(null),
                    conCita.map(x -> x.getCita().getId()).orElse(null),
                    ultimo.getTexto(), ultimo.getDireccion(), ultimo.getCreatedAt(), mensajes.size()));
        });
        return resultado;
    }

    /**
     * Último estado del recordatorio por cita: RESPONDIO si hay un entrante posterior al último
     * recordatorio, si no ENVIADO/FALLIDO del último recordatorio. Citas sin recordatorio no aparecen.
     */
    public Map<UUID, RecordatorioEstadoResponse> estadoRecordatorios(UUID organizacionId, List<UUID> citaIds) {
        if (citaIds == null || citaIds.isEmpty()) return Map.of();
        if (citaIds.size() > MAX_CITAS_ESTADO) {
            throw new IllegalArgumentException("Máximo " + MAX_CITAS_ESTADO + " citas por consulta");
        }
        Map<UUID, Notificacion> ultimaNotif = new LinkedHashMap<>();
        notificacionRepo.findByOrganizacionIdAndCitaIdIn(organizacionId, citaIds).stream()
                .filter(n -> TIPOS_RECORDATORIO.contains(n.getTipo()))
                .forEach(n -> ultimaNotif.merge(n.getCita().getId(), n,
                        (a, b) -> b.getCreadaEn().isAfter(a.getCreadaEn()) ? b : a));
        Map<UUID, WhatsAppMensaje> ultimoEntrante = new LinkedHashMap<>();
        repo.findByOrganizacionIdAndCitaIdInAndDireccion(organizacionId, citaIds, WhatsAppMensaje.ENTRANTE)
                .forEach(m -> ultimoEntrante.merge(m.getCita().getId(), m,
                        (a, b) -> b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a));

        // Canal con el que se envió cada recordatorio (según su mensaje saliente, no el canal actual)
        Set<UUID> porEnlace = ultimaNotif.isEmpty() ? Set.of() : CanalRecordatorio.porEnlace(ultimaNotif.values(),
                repo.findByOrganizacionIdAndCitaIdInAndDireccion(organizacionId, ultimaNotif.keySet(), WhatsAppMensaje.SALIENTE));

        Map<UUID, RecordatorioEstadoResponse> resultado = new LinkedHashMap<>();
        for (UUID id : citaIds) {
            Notificacion n = ultimaNotif.get(id);
            WhatsAppMensaje e = ultimoEntrante.get(id);
            if (e != null && (n == null || !e.getCreatedAt().isBefore(n.getCreadaEn()))) {
                resultado.put(id, new RecordatorioEstadoResponse("RESPONDIO", e.getCreatedAt(), e.getTexto()));
            } else if (n != null) {
                OffsetDateTime fecha = n.getEnviadaEn() != null ? n.getEnviadaEn() : n.getCreadaEn();
                resultado.put(id, new RecordatorioEstadoResponse(n.getEstado(), fecha, null,
                        porEnlace.contains(n.getId()) ? EnlaceWhatsAppSender.PROVEEDOR : null));
            }
        }
        return resultado;
    }

    /** Usado por el simulador: último saliente de la organización a ese teléfono. */
    public Optional<WhatsAppMensaje> ultimoSaliente(UUID organizacionId, String telefono10) {
        return repo.findByOrganizacionIdAndTelefonoAndDireccionOrderByCreatedAtDesc(
                organizacionId, telefono10, WhatsAppMensaje.SALIENTE).stream()
                .max(Comparator.comparing(WhatsAppMensaje::getCreatedAt));
    }
}
