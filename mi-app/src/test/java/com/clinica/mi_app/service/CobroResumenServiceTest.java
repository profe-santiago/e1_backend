package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.CobrosResumenResponse;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Pago;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CobroResumenServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    /** 2026-11-10 10:00 en la clínica (16:00Z reales). */
    @Spy
    private ClinicaReloj clinicaReloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));

    @InjectMocks
    private CobroResumenService service;

    private final UUID orgId = UUID.randomUUID();
    private Medico mariana;
    private Medico jorge;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mariana = medico("Dra. Mariana López");
        jorge = medico("Dr. Jorge Pérez");
    }

    private static Medico medico(String nombre) {
        Medico m = new Medico();
        m.setId(UUID.randomUUID());
        m.setNombre(nombre);
        return m;
    }

    private static Pago pago(Medico medico, String monto, String metodo, String citaFecha, String pagadoEn) {
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setMedico(medico);
        c.setFechaHora(OffsetDateTime.parse(citaFecha));
        Pago p = new Pago();
        p.setId(UUID.randomUUID());
        p.setCita(c);
        p.setMonto(new BigDecimal(monto));
        p.setMetodo(metodo);
        p.setEstado("PAGADO");
        p.setPagadoEn(pagadoEn != null ? OffsetDateTime.parse(pagadoEn) : null);
        return p;
    }

    @Test
    void resumen_agrupaPorMetodoDiaYMedico_usandoCoalescePagadoEnFechaCita() {
        LocalDate desde = LocalDate.of(2026, 11, 8);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        when(pagoRepository.findPagadosEnRango(eq(orgId), any(), any())).thenReturn(List.of(
                // pagadoEn manda sobre la fecha de la cita (cita el 8, pagado el 9)
                pago(mariana, "650.00", "EFECTIVO", "2026-11-08T18:00:00Z", "2026-11-09T09:00:00Z"),
                // sin pagadoEn → se usa la fecha de la cita
                pago(mariana, "650.00", "TARJETA", "2026-11-10T09:00:00Z", null),
                pago(jorge, "500.00", "TRANSFERENCIA", "2026-11-10T11:00:00Z", "2026-11-10T11:35:00Z")
        ));

        CobrosResumenResponse r = service.resumen(orgId, desde, hasta);

        assertEquals(new BigDecimal("1800.00"), r.total());
        assertEquals(3, r.cantidad());
        assertEquals(new BigDecimal("650.00"), r.porMetodo().get("EFECTIVO"));
        assertEquals(new BigDecimal("500.00"), r.porMetodo().get("TRANSFERENCIA"));
        assertEquals(new BigDecimal("650.00"), r.porMetodo().get("TARJETA"));

        assertEquals(3, r.porDia().size(), "rellena todos los días del rango");
        assertEquals(BigDecimal.ZERO, r.porDia().get(0).total());
        assertEquals(new BigDecimal("650.00"), r.porDia().get(1).total());
        assertEquals(new BigDecimal("1150.00"), r.porDia().get(2).total());
        assertEquals(2, r.porDia().get(2).cantidad());

        assertEquals(2, r.porMedico().size());
        assertEquals("Dra. Mariana López", r.porMedico().get(0).nombre(), "ordenado por total descendente");
        assertEquals(new BigDecimal("1300.00"), r.porMedico().get(0).total());
        assertEquals(2, r.porMedico().get(0).cantidad());

        verify(pagoRepository).findPagadosEnRango(orgId,
                OffsetDateTime.parse("2026-11-08T00:00:00Z"), OffsetDateTime.parse("2026-11-11T00:00:00Z"));
    }

    @Test
    void resumen_sinFechas_usaUltimos30DiasHastaHoyDeLaClinica() {
        when(pagoRepository.findPagadosEnRango(eq(orgId), any(), any())).thenReturn(List.of());

        CobrosResumenResponse r = service.resumen(orgId, null, null);

        assertEquals(LocalDate.of(2026, 11, 10), r.hasta());
        assertEquals(LocalDate.of(2026, 10, 12), r.desde());
        assertEquals(30, r.porDia().size());
        assertEquals(BigDecimal.ZERO, r.total());
        assertEquals(3, r.porMetodo().size(), "siempre incluye los 3 métodos");
    }

    @Test
    void resumen_desdePosteriorAHasta_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resumen(orgId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void resumen_rangoMayorA366Dias_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resumen(orgId, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 10)));
    }

    @Test
    void ingresosDelDia_sumaPagosDelDiaDeLaClinica() {
        when(pagoRepository.findPagadosEnRango(orgId,
                OffsetDateTime.parse("2026-11-10T00:00:00Z"), OffsetDateTime.parse("2026-11-11T00:00:00Z")))
                .thenReturn(List.of(
                        pago(mariana, "650.00", "EFECTIVO", "2026-11-10T09:00:00Z", null),
                        pago(jorge, "500.00", "TARJETA", "2026-11-10T11:00:00Z", null)));

        assertEquals(new BigDecimal("1150.00"), service.ingresosDelDia(orgId, LocalDate.of(2026, 11, 10)));
    }
}
