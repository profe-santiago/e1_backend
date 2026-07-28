package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.client.NotifClient;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WhatsAppRespuestaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppRespuestaService.class);

    private final CitaRepository citaRepo;
    private final NotificacionRepository notificacionRepo;
    private final NotifClient notifClient;
    private final AuditClient auditClient;

    public WhatsAppRespuestaService(CitaRepository citaRepo, NotificacionRepository notificacionRepo,
                                     NotifClient notifClient, AuditClient auditClient) {
        this.citaRepo = citaRepo;
        this.notificacionRepo = notificacionRepo;
        this.notifClient = notifClient;
        this.auditClient = auditClient;
    }

    public void procesarRespuesta(String telefono, String mensaje) {
        Optional<Cita> citaOpt = citaRepo
                .findFirstByPaciente_TelefonoAndEstadoAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                        telefono, "SIN_CONFIRMAR", OffsetDateTime.now());
        if (citaOpt.isEmpty()) {
            log.info("WhatsApp: no hay cita SIN_CONFIRMAR para telefono={}", telefono);
            return;
        }
        Cita cita = citaOpt.get();

        String texto = mensaje == null ? "" : mensaje.trim().toLowerCase();
        switch (texto) {
            case "1" -> {
                cita.setEstado("CONFIRMADA");
                citaRepo.save(cita);
            }
            case "2" -> {
                cita.setEstado("CANCELADA");
                cita.setCanceladaPor("PACIENTE");
                cita.setCanceladaEn(OffsetDateTime.now());
                citaRepo.save(cita);

                Notificacion notif = new Notificacion();
                notif.setTipo("CANCELACION_PACIENTE");
                notif.setCanal("WHATSAPP");
                notif.setEstado("ENVIADO");
                notif.setRespuesta(mensaje);
                notif.setCita(cita);
                notif.setOrganizacion(cita.getOrganizacion());
                notificacionRepo.save(notif);
            }
            default -> notifClient.sendNotification("WHATSAPP", telefono,
                    "Responde 1 para confirmar o 2 para cancelar tu cita", cita.getOrganizacion().getId());
        }

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("telefono", telefono);
            metadata.put("mensaje", mensaje);
            auditClient.audit("appointment.updated", "CITA", cita.getId(), cita.getOrganizacion().getId(), null, metadata);
        } catch (Exception ex) {
            log.warn("audit appointment.updated: {}", ex.getMessage());
        }
    }
}
