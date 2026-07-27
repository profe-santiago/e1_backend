package com.clinica.mi_app.service;

import com.clinica.mi_app.client.NotifClient;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final CitaRepository citaRepository;
    private final NotificacionRepository notificacionRepository;
    private final NotifClient notifClient;

    public ReminderJob(CitaRepository citaRepository,
                       NotificacionRepository notificacionRepository,
                       NotifClient notifClient) {
        this.citaRepository = citaRepository;
        this.notificacionRepository = notificacionRepository;
        this.notifClient = notifClient;
    }

    @Scheduled(cron = "${reminder.job.cron:0 */15 * * * *}")
    public void enviarRecordatorios() {
        OffsetDateTime ahora = OffsetDateTime.now();
        procesarVentana(ahora, 48, "RECORDATORIO_48H");
        procesarVentana(ahora, 24, "RECORDATORIO_24H");
    }

    private void procesarVentana(OffsetDateTime ahora, int horas, String tipo) {
        OffsetDateTime desde = ahora.plusHours(horas).minusMinutes(30);
        OffsetDateTime hasta = ahora.plusHours(horas).plusMinutes(30);
        List<Cita> citas = citaRepository.findByFechaHoraBetweenAndEstadoNot(desde, hasta, "CANCELADA");
        for (Cita cita : citas) {
            try {
                procesarCita(cita, tipo);
            } catch (Exception ex) {
                log.error("ReminderJob: error procesando cita={} tipo={}: {}",
                        cita.getId(), tipo, ex.getMessage(), ex);
            }
        }
    }

    // package-private para tests — permite verificar la lógica de negocio sin necesitar un Clock inyectado
    void procesarCita(Cita cita, String tipo) {
        boolean yaEnviado = notificacionRepository.findByCitaId(cita.getId()).stream()
                .anyMatch(n -> tipo.equals(n.getTipo()) && "ENVIADO".equals(n.getEstado()));
        if (yaEnviado) return;

        // Nombre de plantilla en kebab-case del tipo (ej. "recordatorio-48h").
        // Convención REST más común — ajustar si NotifService usa otra nomenclatura.
        String templateName = tipo.toLowerCase().replace("_", "-");
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombrePaciente", cita.getPaciente().getNombre());
        vars.put("nombreMedico", cita.getMedico().getNombre());
        vars.put("fechaHora", cita.getFechaHora().toString());
        vars.put("consultorio", cita.getConsultorio().getNombre());

        Optional<String> resultado = notifClient.sendTemplate(
                templateName, vars, cita.getPaciente().getTelefono(), cita.getOrganizacion().getId());

        // Si Optional vacío, NotifClient falló o el CB está abierto.
        // No guardamos Notificacion para que el job reintente en la siguiente corrida de la misma ventana de 1h.
        if (resultado.isPresent()) {
            Notificacion n = new Notificacion();
            n.setCita(cita);
            n.setOrganizacion(cita.getOrganizacion());
            n.setTipo(tipo);
            n.setEstado("ENVIADO");
            n.setEnviadaEn(OffsetDateTime.now());
            notificacionRepository.save(n);
        }
    }
}
