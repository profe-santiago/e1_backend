package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.ActividadResponse;
import com.clinica.mi_app.dto.response.ProximoDiaResponse;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import com.clinica.mi_app.whatsapp.CanalRecordatorio;
import com.clinica.mi_app.whatsapp.EnlaceWhatsAppSender;
import com.clinica.mi_app.whatsapp.TelefonoMx;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Vista "Actividad" (antes "Notificaciones"): respuestas de los pacientes y recordatorios enviados,
 * siempre acotados a la organización del JWT y, para el rol MEDICO, a sus propias citas.
 * No requiere cambios de base: todo sale de la tabla notificacion.
 */
@Service
@Transactional(readOnly = true)
public class ActividadService {

    public static final String CONFIRMACION = WhatsAppRespuestaService.CONFIRMACION_PACIENTE;
    public static final String CANCELACION = WhatsAppRespuestaService.CANCELACION_PACIENTE;
    public static final List<String> RECORDATORIOS = List.of("RECORDATORIO_48H", "RECORDATORIO_24H", RecordatorioService.MANUAL);
    public static final List<String> TIPOS = List.of(CONFIRMACION, CANCELACION,
            "RECORDATORIO_48H", "RECORDATORIO_24H", RecordatorioService.MANUAL);

    static final int DIAS_VENTANA = 30;
    static final int DIAS_PROXIMO = 7;

    public enum Filtro {
        TODAS, SIN_LEER, CONFIRMACIONES, CANCELACIONES, RECORDATORIOS;

        public static Filtro de(String valor) {
            if (valor == null || valor.isBlank()) return TODAS;
            try {
                return Filtro.valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Filtro inválido: usa SIN_LEER, CONFIRMACIONES, CANCELACIONES o RECORDATORIOS");
            }
        }
    }

    private final NotificacionRepository notificacionRepo;
    private final CitaRepository citaRepo;
    private final WhatsAppMensajeRepository mensajeRepo;
    private final ClinicaReloj reloj;

    public ActividadService(NotificacionRepository notificacionRepo, CitaRepository citaRepo,
                            WhatsAppMensajeRepository mensajeRepo, ClinicaReloj reloj) {
        this.notificacionRepo = notificacionRepo;
        this.citaRepo = citaRepo;
        this.mensajeRepo = mensajeRepo;
        this.reloj = reloj;
    }

    // ── Lista de actividad ──

    public ActividadResponse actividad(UUID organizacionId, UUID medicoId, String filtro) {
        Filtro f = Filtro.de(filtro);
        OffsetDateTime desde = OffsetDateTime.now(ZoneOffset.UTC).minusDays(DIAS_VENTANA);
        List<Notificacion> todas = medicoId == null
                ? notificacionRepo.findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(
                        organizacionId, TIPOS, desde)
                : notificacionRepo.findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualAndCita_Medico_IdOrderByCreadaEnDesc(
                        organizacionId, TIPOS, desde, medicoId);

        ActividadResponse.Conteos conteos = new ActividadResponse.Conteos(
                todas.size(),
                todas.stream().filter(n -> !Boolean.TRUE.equals(n.getLeida())).count(),
                todas.stream().filter(n -> CONFIRMACION.equals(n.getTipo())).count(),
                todas.stream().filter(n -> CANCELACION.equals(n.getTipo())).count(),
                todas.stream().filter(n -> RECORDATORIOS.contains(n.getTipo())).count());

        List<Notificacion> filtradas = todas.stream().filter(predicado(f)).toList();
        Set<UUID> conEnlace = recordatoriosPorEnlace(organizacionId, filtradas);
        List<ActividadResponse.Item> items = filtradas.stream()
                .map(n -> item(n, conEnlace.contains(n.getId())))
                .toList();
        return new ActividadResponse(items, conteos);
    }

    @Transactional
    public int marcarTodasLeidas(UUID organizacionId, UUID medicoId) {
        List<Notificacion> pendientes = medicoId == null
                ? notificacionRepo.findByOrganizacionIdAndTipoInAndLeidaFalse(organizacionId, TIPOS)
                : notificacionRepo.findByOrganizacionIdAndTipoInAndCita_Medico_IdAndLeidaFalse(organizacionId, TIPOS, medicoId);
        pendientes.forEach(n -> n.setLeida(true));
        notificacionRepo.saveAll(pendientes);
        return pendientes.size();
    }

    static Predicate<Notificacion> predicado(Filtro f) {
        return switch (f) {
            case TODAS -> n -> true;
            case SIN_LEER -> n -> !Boolean.TRUE.equals(n.getLeida());
            case CONFIRMACIONES -> n -> CONFIRMACION.equals(n.getTipo());
            case CANCELACIONES -> n -> CANCELACION.equals(n.getTipo());
            case RECORDATORIOS -> n -> RECORDATORIOS.contains(n.getTipo());
        };
    }

    /** Ids de recordatorios enviados por el canal enlace (según su mensaje saliente). */
    private Set<UUID> recordatoriosPorEnlace(UUID organizacionId, List<Notificacion> notifs) {
        List<Notificacion> recordatorios = notifs.stream()
                .filter(n -> RECORDATORIOS.contains(n.getTipo()) && n.getCita() != null).toList();
        if (recordatorios.isEmpty()) return Set.of();
        Set<UUID> citaIds = recordatorios.stream().map(n -> n.getCita().getId()).collect(Collectors.toSet());
        return CanalRecordatorio.porEnlace(recordatorios,
                mensajeRepo.findByOrganizacionIdAndCitaIdInAndDireccion(organizacionId, citaIds, WhatsAppMensaje.SALIENTE));
    }

    private static ActividadResponse.Item item(Notificacion n, boolean porEnlace) {
        Cita c = n.getCita();
        String categoria = CONFIRMACION.equals(n.getTipo()) ? "CONFIRMACION"
                : CANCELACION.equals(n.getTipo()) ? "CANCELACION" : "RECORDATORIO";
        String origen = "RECORDATORIO".equals(categoria) ? null
                : (n.getRespuesta() != null && n.getRespuesta().toLowerCase().contains("enlace") ? "ENLACE" : "WHATSAPP");
        return new ActividadResponse.Item(n.getId(), n.getTipo(), categoria, n.getEstado(),
                Boolean.TRUE.equals(n.getLeida()), n.getCreadaEn(), origen,
                porEnlace ? EnlaceWhatsAppSender.PROVEEDOR : null,
                c == null ? null : c.getId(),
                c == null ? null : c.getFechaHora(),
                c == null ? null : c.getEstado(),
                c == null || c.getPaciente() == null ? null : c.getPaciente().getNombre(),
                c == null || c.getMedico() == null ? null : c.getMedico().getId(),
                c == null || c.getMedico() == null ? null : c.getMedico().getNombre());
    }

    // ── Encabezado "Mañana" ──

    /**
     * Resumen del próximo día con citas, empezando por mañana (reloj de la clínica) y buscando hasta
     * {@value #DIAS_PROXIMO} días adelante. Si no hay ninguna, {@link ProximoDiaResponse#vacio()}.
     */
    public ProximoDiaResponse proximoDia(UUID organizacionId, UUID medicoId) {
        LocalDate manana = reloj.hoy().plusDays(1);
        List<Cita> citas = citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                        organizacionId, ClinicaReloj.inicioDelDia(manana),
                        ClinicaReloj.inicioDelDia(manana.plusDays(DIAS_PROXIMO))).stream()
                .filter(c -> medicoId == null || (c.getMedico() != null && medicoId.equals(c.getMedico().getId())))
                .toList();
        if (citas.isEmpty()) return ProximoDiaResponse.vacio();

        // Convención: la fecha de la cita es su parte UTC (hora de reloj de la clínica)
        LocalDate fecha = citas.stream()
                .map(c -> c.getFechaHora().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate())
                .min(Comparator.naturalOrder()).orElseThrow();
        List<Cita> delDia = citas.stream()
                .filter(c -> c.getFechaHora().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().equals(fecha))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .toList();

        List<ProximoDiaResponse.Pendiente> pendientes = delDia.stream()
                .filter(c -> "SIN_CONFIRMAR".equals(c.getEstado()))
                .map(c -> new ProximoDiaResponse.Pendiente(c.getId(), c.getFechaHora(), c.getEstado(),
                        c.getPaciente() == null ? null : c.getPaciente().getNombre(),
                        c.getMedico() == null ? null : c.getMedico().getId(),
                        c.getMedico() == null ? null : c.getMedico().getNombre(),
                        c.getPaciente() != null && TelefonoMx.aDiezDigitos(c.getPaciente().getTelefono()) != null))
                .toList();

        List<ProximoDiaResponse.CitaDelDia> todas = delDia.stream()
                .map(c -> new ProximoDiaResponse.CitaDelDia(c.getId(), c.getFechaHora(), c.getEstado(),
                        c.getPaciente() == null ? null : c.getPaciente().getNombre(),
                        c.getMedico() == null ? null : c.getMedico().getId(),
                        c.getMedico() == null ? null : c.getMedico().getNombre()))
                .toList();

        return new ProximoDiaResponse(fecha, fecha.equals(manana), delDia.size(),
                contar(delDia, "CONFIRMADA"), contar(delDia, "CANCELADA"), contar(delDia, "SIN_CONFIRMAR"),
                pendientes, todas);
    }

    private static long contar(List<Cita> citas, String estado) {
        return citas.stream().filter(c -> estado.equals(c.getEstado())).count();
    }
}
