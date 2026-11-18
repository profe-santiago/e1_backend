package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.whatsapp.EnlaceWhatsAppSender;
import com.clinica.mi_app.whatsapp.RecordatorioPlantilla;
import com.clinica.mi_app.whatsapp.TelefonoMx;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aplica la respuesta del paciente a una cita.
 *
 * <ul>
 *   <li>Por WhatsApp (webhook ya resuelto por WhatsAppWebhookProcessor): "1" confirma, "2" cancela
 *       (generan CONFIRMACION_PACIENTE / CANCELACION_PACIENTE para la campana y Actividad); en ambos casos
 *       se le contesta al paciente.
 *       Cualquier otro texto recibe el mensaje de ayuda.</li>
 *   <li>Desde el enlace público (/c/{token}): misma lógica de confirmar/cancelar, pero se registra un
 *       mensaje ENTRANTE ("Confirmó desde el enlace" / "Canceló desde el enlace") y no se envía respuesta
 *       por WhatsApp: la confirmación la ve el paciente en la propia página. La confirmación también
 *       genera CONFIRMACION_PACIENTE para la campana.</li>
 * </ul>
 */
@Service
public class WhatsAppRespuestaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppRespuestaService.class);

    public static final String CANCELACION_PACIENTE = "CANCELACION_PACIENTE";
    public static final String CONFIRMACION_PACIENTE = "CONFIRMACION_PACIENTE";

    static final Set<String> CONFIRMAR = Set.of("1", "confirmar", "confirmo");
    static final Set<String> CANCELAR = Set.of("2", "cancelar", "cancelo");

    private final CitaRepository citaRepo;
    private final NotificacionRepository notificacionRepo;
    private final WhatsAppMensajeria mensajeria;
    private final AuditClient auditClient;
    private final ClinicaReloj reloj;

    public WhatsAppRespuestaService(CitaRepository citaRepo, NotificacionRepository notificacionRepo,
                                    WhatsAppMensajeria mensajeria, AuditClient auditClient, ClinicaReloj reloj) {
        this.citaRepo = citaRepo;
        this.notificacionRepo = notificacionRepo;
        this.mensajeria = mensajeria;
        this.auditClient = auditClient;
        this.reloj = reloj;
    }

    // ── Respuesta por WhatsApp (webhook / simulador) ──

    public void procesarRespuesta(Cita cita, String telefono, String mensaje) {
        String texto = mensaje == null ? "" : mensaje.trim().toLowerCase();

        if (CONFIRMAR.contains(texto)) {
            confirmar(cita);
            notificar(cita, CONFIRMACION_PACIENTE, mensaje);
            mensajeria.enviarTexto(cita.getOrganizacion(), cita, telefono, RecordatorioPlantilla.confirmada(cita));
        } else if (CANCELAR.contains(texto)) {
            cancelar(cita);
            notificar(cita, CANCELACION_PACIENTE, mensaje);
            mensajeria.enviarTexto(cita.getOrganizacion(), cita, telefono, RecordatorioPlantilla.cancelada(cita));
        } else {
            mensajeria.enviarTexto(cita.getOrganizacion(), cita, telefono, RecordatorioPlantilla.ayuda());
        }

        auditar(cita, telefono, mensaje, mensajeria.proveedor());
    }

    // ── Respuesta desde el enlace público ──

    public void confirmarDesdeEnlace(Cita cita) {
        confirmar(cita);
        notificar(cita, CONFIRMACION_PACIENTE, RecordatorioPlantilla.CONFIRMO_ENLACE);
        registrarEntranteEnlace(cita, RecordatorioPlantilla.CONFIRMO_ENLACE);
    }

    public void cancelarDesdeEnlace(Cita cita) {
        cancelar(cita);
        notificar(cita, CANCELACION_PACIENTE, RecordatorioPlantilla.CANCELO_ENLACE);
        registrarEntranteEnlace(cita, RecordatorioPlantilla.CANCELO_ENLACE);
    }

    // ── Lógica común ──

    private void confirmar(Cita cita) {
        cita.setEstado("CONFIRMADA");
        citaRepo.save(cita);
    }

    private void cancelar(Cita cita) {
        cita.setEstado("CANCELADA");
        cita.setCanceladaPor("PACIENTE");
        cita.setCanceladaEn(reloj.ahora());
        citaRepo.save(cita);
    }

    private void notificar(Cita cita, String tipo, String respuesta) {
        Notificacion notif = new Notificacion();
        notif.setTipo(tipo);
        notif.setCanal("WHATSAPP");
        notif.setEstado("ENVIADO");
        notif.setRespuesta(respuesta);
        notif.setEnviadaEn(OffsetDateTime.now());
        notif.setCita(cita);
        notif.setOrganizacion(cita.getOrganizacion());
        notificacionRepo.save(notif);
    }

    private void registrarEntranteEnlace(Cita cita, String texto) {
        String raw = cita.getPaciente() == null ? null : cita.getPaciente().getTelefono();
        String telefono = TelefonoMx.aDiezDigitos(raw);
        if (telefono == null) telefono = raw == null ? "" : raw;
        mensajeria.registrarEntrante(cita.getOrganizacion(), cita, telefono, texto, null);
        auditar(cita, telefono, texto, EnlaceWhatsAppSender.PROVEEDOR);
    }

    private void auditar(Cita cita, String telefono, String mensaje, String canal) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("telefono", telefono);
            metadata.put("mensaje", mensaje);
            metadata.put("canal", canal);
            auditClient.audit("appointment.updated", "CITA", cita.getId(), cita.getOrganizacion().getId(), null, metadata);
        } catch (Exception ex) {
            log.warn("audit appointment.updated: {}", ex.getMessage());
        }
    }
}
