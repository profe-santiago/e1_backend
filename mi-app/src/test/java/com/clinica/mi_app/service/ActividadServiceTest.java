package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.ActividadResponse;
import com.clinica.mi_app.dto.response.ProximoDiaResponse;
import com.clinica.mi_app.model.*;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActividadServiceTest {

    @Mock private NotificacionRepository notificacionRepo;
    @Mock private CitaRepository citaRepo;
    @Mock private WhatsAppMensajeRepository mensajeRepo;

    /** Martes 17/11/2026 16:00Z reales = 10:00 en la clínica → hoy = 17/11, mañana = miércoles 18/11. */
    private ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-17T16:00:00Z"), ZoneOffset.UTC));
    private ActividadService service;

    private final UUID orgId = UUID.randomUUID();
    private final Medico mariana = medico();
    private final Medico otro = medico();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new ActividadService(notificacionRepo, citaRepo, mensajeRepo, reloj);
        when(mensajeRepo.findByOrganizacionIdAndCitaIdInAndDireccion(any(), any(), any())).thenReturn(List.of());
    }

    // ── Lista de actividad ──

    @Test
    void medico_consultaSoloSusCitas_admin_todaLaOrganizacion() {
        service.actividad(orgId, mariana.getId(), null);
        verify(notificacionRepo).findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualAndCita_Medico_IdOrderByCreadaEnDesc(
                eq(orgId), eq(ActividadService.TIPOS), any(), eq(mariana.getId()));
        verify(notificacionRepo, never()).findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(any(), any(), any());

        service.actividad(orgId, null, null);
        verify(notificacionRepo).findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(
                eq(orgId), eq(ActividadService.TIPOS), any());
    }

    @Test
    void ventanaDe30Dias() {
        service.actividad(orgId, null, null);
        verify(notificacionRepo).findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(
                eq(orgId), any(), argThatDesdeHace(30));
    }

    @Test
    void filtrosYConteos() {
        Cita cita = cita(mariana, "2026-11-18T11:00:00Z", "CONFIRMADA");
        List<Notificacion> todas = List.of(
                notif(cita, "CONFIRMACION_PACIENTE", false, "Confirmó desde el enlace"),
                notif(cita, "CANCELACION_PACIENTE", true, "2"),
                notif(cita, "RECORDATORIO_MANUAL", false, null),
                notif(cita, "RECORDATORIO_24H", true, null));
        when(notificacionRepo.findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(any(), any(), any()))
                .thenReturn(todas);

        ActividadResponse r = service.actividad(orgId, null, "");
        assertEquals(new ActividadResponse.Conteos(4, 2, 1, 1, 2), r.conteos());
        assertEquals(4, r.items().size());

        assertEquals(List.of("CONFIRMACION_PACIENTE", "RECORDATORIO_MANUAL"),
                tipos(service.actividad(orgId, null, "SIN_LEER")));
        assertEquals(List.of("CONFIRMACION_PACIENTE"), tipos(service.actividad(orgId, null, "confirmaciones")));
        assertEquals(List.of("CANCELACION_PACIENTE"), tipos(service.actividad(orgId, null, "CANCELACIONES")));
        assertEquals(List.of("RECORDATORIO_MANUAL", "RECORDATORIO_24H"), tipos(service.actividad(orgId, null, "RECORDATORIOS")));
        // Los conteos no cambian con el filtro
        assertEquals(4, service.actividad(orgId, null, "CANCELACIONES").conteos().todas());

        assertThrows(IllegalArgumentException.class, () -> service.actividad(orgId, null, "OTRO"));
    }

    @Test
    void item_categoriaOrigenYDatosDeLaCita() {
        Cita cita = cita(mariana, "2026-11-18T11:00:00Z", "CONFIRMADA");
        when(notificacionRepo.findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(any(), any(), any()))
                .thenReturn(List.of(
                        notif(cita, "CONFIRMACION_PACIENTE", false, "Confirmó desde el enlace"),
                        notif(cita, "CONFIRMACION_PACIENTE", false, "1")));

        List<ActividadResponse.Item> items = service.actividad(orgId, null, null).items();

        assertEquals("CONFIRMACION", items.get(0).categoria());
        assertEquals("ENLACE", items.get(0).origen());
        assertEquals("WHATSAPP", items.get(1).origen(), "webhook de Twilio/Meta → 'Por WhatsApp'");
        assertEquals("Ana Sofía Ramírez Mendoza", items.get(0).pacienteNombre());
        assertEquals(mariana.getId(), items.get(0).medicoId());
        assertEquals(OffsetDateTime.parse("2026-11-18T11:00:00Z"), items.get(0).citaFechaHora());
    }

    @Test
    void recordatorio_porEnlace_seMarcaComoEnlace() {
        Cita cita = cita(mariana, "2026-11-18T11:00:00Z", "SIN_CONFIRMAR");
        Notificacion porEnlace = notif(cita, "RECORDATORIO_MANUAL", false, null);
        Cita otraCita = cita(mariana, "2026-11-18T12:00:00Z", "SIN_CONFIRMAR");
        Notificacion simulado = notif(otraCita, "RECORDATORIO_MANUAL", false, null);
        when(notificacionRepo.findTop200ByOrganizacionIdAndTipoInAndCreadaEnGreaterThanEqualOrderByCreadaEnDesc(any(), any(), any()))
                .thenReturn(List.of(porEnlace, simulado));
        when(mensajeRepo.findByOrganizacionIdAndCitaIdInAndDireccion(eq(orgId), any(), eq(WhatsAppMensaje.SALIENTE)))
                .thenReturn(List.of(
                        saliente(cita, "enlace-123", porEnlace.getEnviadaEn()),
                        saliente(otraCita, "wamid.SIM.1", simulado.getEnviadaEn())));

        List<ActividadResponse.Item> items = service.actividad(orgId, null, "RECORDATORIOS").items();

        assertEquals("enlace", items.get(0).canalEnvio());
        assertNull(items.get(1).canalEnvio());
    }

    @Test
    void marcarTodas_respetaOrganizacionYMedico() {
        Notificacion a = notif(cita(mariana, "2026-11-18T11:00:00Z", "CONFIRMADA"), "RECORDATORIO_24H", false, null);
        when(notificacionRepo.findByOrganizacionIdAndTipoInAndCita_Medico_IdAndLeidaFalse(orgId, ActividadService.TIPOS, mariana.getId()))
                .thenReturn(List.of(a));

        assertEquals(1, service.marcarTodasLeidas(orgId, mariana.getId()));
        assertTrue(a.getLeida());
        verify(notificacionRepo, never()).findByOrganizacionIdAndTipoInAndLeidaFalse(any(), any());
    }

    // ── Encabezado "Mañana" ──

    @Test
    void manana_conCitas_resumenYPendientesDelMedico() {
        when(citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                orgId, OffsetDateTime.parse("2026-11-18T00:00:00Z"), OffsetDateTime.parse("2026-11-25T00:00:00Z")))
                .thenReturn(List.of(
                        cita(mariana, "2026-11-18T11:00:00Z", "SIN_CONFIRMAR"),
                        cita(mariana, "2026-11-18T09:00:00Z", "CONFIRMADA"),
                        cita(mariana, "2026-11-18T10:00:00Z", "CANCELADA"),
                        cita(otro, "2026-11-18T09:30:00Z", "SIN_CONFIRMAR"),
                        cita(mariana, "2026-11-19T10:00:00Z", "SIN_CONFIRMAR")));

        ProximoDiaResponse r = service.proximoDia(orgId, mariana.getId());

        assertEquals(LocalDate.of(2026, 11, 18), r.fecha());
        assertTrue(r.esManana());
        assertEquals(3, r.total());
        assertEquals(1, r.confirmadas());
        assertEquals(1, r.canceladas());
        assertEquals(1, r.sinConfirmar());
        assertEquals(1, r.pendientes().size());
        assertEquals(OffsetDateTime.parse("2026-11-18T11:00:00Z"), r.pendientes().get(0).fechaHora());
        assertTrue(r.pendientes().get(0).telefonoValido());

        // Tira de horas: todas las citas del día del médico (incluida la cancelada), ordenadas por hora
        assertEquals(List.of("09:00", "10:00", "11:00"),
                r.citas().stream().map(c -> c.fechaHora().toLocalTime().toString()).toList());
        assertEquals(List.of("CONFIRMADA", "CANCELADA", "SIN_CONFIRMAR"),
                r.citas().stream().map(ProximoDiaResponse.CitaDelDia::estado).toList());
        assertEquals("Ana Sofía Ramírez Mendoza", r.citas().get(0).pacienteNombre());
    }

    @Test
    void manana_sinCitas_devuelveElProximoDiaConCitas() {
        // Viernes 20/11 10:00 en la clínica → mañana sábado 21 y domingo 22 sin citas → lunes 23
        reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-20T16:00:00Z"), ZoneOffset.UTC));
        service = new ActividadService(notificacionRepo, citaRepo, mensajeRepo, reloj);
        when(citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                orgId, OffsetDateTime.parse("2026-11-21T00:00:00Z"), OffsetDateTime.parse("2026-11-28T00:00:00Z")))
                .thenReturn(List.of(
                        cita(mariana, "2026-11-24T09:00:00Z", "SIN_CONFIRMAR"),
                        cita(mariana, "2026-11-23T10:30:00Z", "SIN_CONFIRMAR"),
                        cita(mariana, "2026-11-23T09:00:00Z", "CONFIRMADA")));

        ProximoDiaResponse r = service.proximoDia(orgId, mariana.getId());

        assertEquals(LocalDate.of(2026, 11, 23), r.fecha());
        assertFalse(r.esManana());
        assertEquals(2, r.total());
        assertEquals(1, r.sinConfirmar());
    }

    @Test
    void manana_usaElRelojDeLaClinica_noUTC() {
        // 17/11 23:30 en la clínica = 18/11 05:30Z reales → "mañana" sigue siendo el 18/11
        reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-18T05:30:00Z"), ZoneOffset.UTC));
        service = new ActividadService(notificacionRepo, citaRepo, mensajeRepo, reloj);

        service.proximoDia(orgId, null);

        verify(citaRepo).findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                orgId, OffsetDateTime.parse("2026-11-18T00:00:00Z"), OffsetDateTime.parse("2026-11-25T00:00:00Z"));
    }

    @Test
    void manana_sinCitasEnLos7Dias_vacio() {
        when(citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(any(), any(), any()))
                .thenReturn(List.of(cita(otro, "2026-11-18T09:00:00Z", "SIN_CONFIRMAR")));

        ProximoDiaResponse r = service.proximoDia(orgId, mariana.getId());

        assertNull(r.fecha());
        assertEquals(0, r.total());
        assertTrue(r.pendientes().isEmpty());
        assertTrue(r.citas().isEmpty());
    }

    // ── Helpers ──

    private static OffsetDateTime argThatDesdeHace(int dias) {
        return org.mockito.ArgumentMatchers.argThat(d -> {
            long horas = Duration.between(d, OffsetDateTime.now(ZoneOffset.UTC)).toHours();
            return horas >= dias * 24L - 1 && horas <= dias * 24L + 1;
        });
    }

    private static List<String> tipos(ActividadResponse r) {
        return r.items().stream().map(ActividadResponse.Item::tipo).toList();
    }

    private static Medico medico() {
        Medico m = new Medico();
        m.setId(UUID.randomUUID());
        m.setNombre("Dra. Mariana López Ruiz");
        return m;
    }

    private Cita cita(Medico medico, String fechaHora, String estado) {
        Organizacion o = new Organizacion();
        o.setId(orgId);
        Paciente p = new Paciente();
        p.setNombre("Ana Sofía Ramírez Mendoza");
        p.setTelefono("9511234567");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(o);
        c.setMedico(medico);
        c.setPaciente(p);
        c.setEstado(estado);
        c.setFechaHora(OffsetDateTime.parse(fechaHora));
        return c;
    }

    private static Notificacion notif(Cita cita, String tipo, boolean leida, String respuesta) {
        Notificacion n = new Notificacion();
        n.setId(UUID.randomUUID());
        n.setCita(cita);
        n.setOrganizacion(cita.getOrganizacion());
        n.setTipo(tipo);
        n.setEstado("ENVIADO");
        n.setLeida(leida);
        n.setRespuesta(respuesta);
        n.setEnviadaEn(OffsetDateTime.parse("2026-11-17T15:00:00Z"));
        n.setCreadaEn(OffsetDateTime.parse("2026-11-17T15:00:00Z"));
        return n;
    }

    private static WhatsAppMensaje saliente(Cita cita, String wamid, OffsetDateTime cuando) {
        WhatsAppMensaje m = new WhatsAppMensaje();
        m.setCita(cita);
        m.setDireccion(WhatsAppMensaje.SALIENTE);
        m.setWamid(wamid);
        m.setCreatedAt(cuando.plusSeconds(1));
        return m;
    }
}
