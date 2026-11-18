package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.repository.CitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Recordatorios automáticos a 48 h y 24 h. Delega en {@link RecordatorioService} (mismas validaciones
 * que el envío manual). Con {@code notif.provider=twilio} no envía salvo
 * {@code whatsapp.twilio.job-enabled=true}: el sandbox tiene cupo de mensajes y un solo destinatario.
 * Con {@code notif.provider=enlace} nunca envía: el mensaje lo abre una persona en wa.me con el botón
 * manual; si el job corriera marcaría como ENVIADO un recordatorio que nadie mandó.
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final CitaRepository citaRepository;
    private final RecordatorioService recordatorioService;
    private final ClinicaReloj reloj;
    private final boolean habilitado;

    public ReminderJob(CitaRepository citaRepository,
                       RecordatorioService recordatorioService,
                       ClinicaReloj reloj,
                       @Value("${notif.provider:simulado}") String provider,
                       @Value("${whatsapp.twilio.job-enabled:false}") boolean twilioJobEnabled) {
        this.citaRepository = citaRepository;
        this.recordatorioService = recordatorioService;
        this.reloj = reloj;
        this.habilitado = !"enlace".equalsIgnoreCase(provider)
                && (!"twilio".equalsIgnoreCase(provider) || twilioJobEnabled);
    }

    @Scheduled(cron = "${reminder.job.cron:0 */15 * * * *}")
    public void enviarRecordatorios() {
        if (!habilitado) {
            log.debug("ReminderJob deshabilitado para este proveedor (enlace, o twilio sin job-enabled)");
            return;
        }
        // Hora de reloj de la clínica expresada en UTC, igual que cita.fecha_hora
        OffsetDateTime ahora = reloj.ahora();
        procesarVentana(ahora, 48, "RECORDATORIO_48H");
        procesarVentana(ahora, 24, "RECORDATORIO_24H");
    }

    boolean isHabilitado() {
        return habilitado;
    }

    private void procesarVentana(OffsetDateTime ahora, int horas, String tipo) {
        OffsetDateTime desde = ahora.plusHours(horas).minusMinutes(30);
        OffsetDateTime hasta = ahora.plusHours(horas).plusMinutes(30);
        List<Cita> citas = citaRepository.findByFechaHoraBetweenAndEstadoNot(desde, hasta, "CANCELADA");
        for (Cita cita : citas) {
            try {
                recordatorioService.enviarAutomatico(cita.getId(), tipo);
            } catch (Exception ex) {
                log.error("ReminderJob: error procesando cita={} tipo={}: {}", cita.getId(), tipo, ex.getMessage(), ex);
            }
        }
    }
}
