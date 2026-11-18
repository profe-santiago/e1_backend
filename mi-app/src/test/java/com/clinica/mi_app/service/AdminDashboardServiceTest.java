package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.AdminDashboardResponse;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.model.Pago;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminDashboardServiceTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private CobroResumenService cobroResumenService;

    /** 2026-11-10 10:00 en la clínica (16:00Z reales) → ahora = 2026-11-10T10:00Z. */
    @Spy
    private ClinicaReloj clinicaReloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));

    @InjectMocks
    private AdminDashboardService service;

    private final UUID orgId = UUID.randomUUID();
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-10T10:00:00Z");
    private static final OffsetDateTime INICIO_HOY = OffsetDateTime.parse("2026-11-10T00:00:00Z");
    private static final OffsetDateTime INICIO_MANANA = OffsetDateTime.parse("2026-11-11T00:00:00Z");

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static Cita cita(String estado, String fechaHora) {
        Paciente p = new Paciente();
        p.setId(UUID.randomUUID());
        p.setNombre("Ana Ramírez");
        p.setTelefono("5512345678");
        Medico m = new Medico();
        m.setId(UUID.randomUUID());
        m.setNombre("Dra. Mariana López");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setEstado(estado);
        c.setFechaHora(OffsetDateTime.parse(fechaHora));
        c.setPaciente(p);
        c.setMedico(m);
        return c;
    }

    private static Pago pendiente(String monto) {
        Pago p = new Pago();
        p.setMonto(new BigDecimal(monto));
        p.setEstado("PENDIENTE");
        return p;
    }

    @Test
    void dashboard_calculaMetricasConElRelojDeLaClinica() {
        when(citaRepository.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(orgId, INICIO_HOY, INICIO_MANANA))
                .thenReturn(List.of(
                        cita("CONFIRMADA", "2026-11-10T09:00:00Z"),
                        cita("CONFIRMADA", "2026-11-10T11:00:00Z"),
                        cita("SIN_CONFIRMAR", "2026-11-10T12:00:00Z"),
                        cita("CANCELADA", "2026-11-10T13:00:00Z")));
        when(cobroResumenService.ingresosDelDia(orgId, LocalDate.of(2026, 11, 10))).thenReturn(new BigDecimal("1300.00"));
        when(pagoRepository.findByOrganizacionIdAndEstado(orgId, "PENDIENTE"))
                .thenReturn(List.of(pendiente("650.00"), pendiente("500.00")));
        when(citaRepository.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                orgId, OffsetDateTime.parse("2026-10-11T00:00:00Z"), AHORA))
                .thenReturn(List.of(
                        cita("CONFIRMADA", "2026-11-01T09:00:00Z"),
                        cita("CONFIRMADA", "2026-11-02T09:00:00Z"),
                        cita("CONFIRMADA", "2026-11-03T09:00:00Z"),
                        cita("NO_ASISTIO", "2026-11-04T09:00:00Z"),
                        cita("CANCELADA", "2026-11-05T09:00:00Z"),
                        cita("REAGENDADA", "2026-11-06T09:00:00Z")));
        Cita proxima = cita("CONFIRMADA", "2026-11-10T11:00:00Z");
        Cita sinConfirmar = cita("SIN_CONFIRMAR", "2026-11-10T12:00:00Z");
        when(citaRepository.findTop8ByOrganizacionIdAndEstadoNotInAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                eq(orgId), any(), eq(AHORA))).thenReturn(List.of(proxima, sinConfirmar));
        when(citaRepository.findTop10ByOrganizacionIdAndEstadoAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                orgId, "SIN_CONFIRMAR", AHORA)).thenReturn(List.of(sinConfirmar));

        AdminDashboardResponse r = service.dashboard(orgId);

        assertEquals(LocalDate.of(2026, 11, 10), r.fecha());
        assertEquals(4, r.citasHoyTotal());
        assertEquals(2L, r.citasHoyPorEstado().get("CONFIRMADA"));
        assertEquals(1L, r.citasHoyPorEstado().get("SIN_CONFIRMAR"));
        assertEquals(1L, r.citasHoyPorEstado().get("CANCELADA"));
        assertEquals(0L, r.citasHoyPorEstado().get("NO_ASISTIO"), "incluye todos los estados aunque estén en 0");
        assertEquals(5, r.citasHoyPorEstado().size());

        assertEquals(new BigDecimal("1300.00"), r.ingresosHoy());
        assertEquals(2, r.cobrosPendientesCantidad());
        assertEquals(new BigDecimal("1150.00"), r.cobrosPendientesMonto());

        // base = 4 (sin CANCELADA ni REAGENDADA): 3 confirmadas = 75 %, 1 no asistió = 25 %
        assertEquals(4, r.tasas30Dias().base());
        assertEquals(75.0, r.tasas30Dias().confirmacion());
        assertEquals(25.0, r.tasas30Dias().noAsistencia());

        assertEquals(2, r.proximasCitas().size());
        assertEquals(proxima.getId(), r.proximasCitas().get(0).id());
        assertEquals("Ana Ramírez", r.proximasCitas().get(0).pacienteNombre());
        assertEquals("Dra. Mariana López", r.proximasCitas().get(0).medicoNombre());
        assertEquals(1, r.proximasSinConfirmar().size());

        verify(citaRepository).findTop8ByOrganizacionIdAndEstadoNotInAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                eq(orgId), eq(AdminDashboardService.EXCLUIDOS_DE_PROXIMAS), eq(AHORA));
    }

    @Test
    void calcularTasas_sinCitas_devuelveCeros() {
        AdminDashboardResponse.Tasas t = AdminDashboardService.calcularTasas(List.of());
        assertEquals(0, t.base());
        assertEquals(0.0, t.confirmacion());
        assertEquals(0.0, t.noAsistencia());
    }

    @Test
    void calcularTasas_redondeaAUnDecimal() {
        AdminDashboardResponse.Tasas t = AdminDashboardService.calcularTasas(List.of(
                cita("CONFIRMADA", "2026-11-01T09:00:00Z"),
                cita("SIN_CONFIRMAR", "2026-11-02T09:00:00Z"),
                cita("NO_ASISTIO", "2026-11-03T09:00:00Z")));
        assertEquals(33.3, t.confirmacion());
        assertEquals(33.3, t.noAsistencia());
        assertEquals(3, t.base());
    }
}
