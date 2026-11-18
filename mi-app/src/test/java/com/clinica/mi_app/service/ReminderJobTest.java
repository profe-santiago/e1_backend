package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ReminderJobTest {

    @Mock private CitaRepository citaRepository;
    @Mock private RecordatorioService recordatorioService;

    /** 16:00Z reales = 10:00 en la clínica → ahora = 2026-11-10T10:00Z (convención de reloj en UTC). */
    private final ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-10T10:00:00Z");

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private ReminderJob job(String provider, boolean twilioJob) {
        return new ReminderJob(citaRepository, recordatorioService, reloj, provider, twilioJob);
    }

    @Test
    public void enviarRecordatorios_usaVentanasConElRelojDeLaClinica() {
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any())).thenReturn(List.of());

        job("simulado", false).enviarRecordatorios();

        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hasta = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(citaRepository, times(2)).findByFechaHoraBetweenAndEstadoNot(desde.capture(), hasta.capture(), eq("CANCELADA"));
        assertEquals(AHORA.plusHours(47).plusMinutes(30), desde.getAllValues().get(0));
        assertEquals(AHORA.plusHours(48).plusMinutes(30), hasta.getAllValues().get(0));
        assertEquals(AHORA.plusHours(23).plusMinutes(30), desde.getAllValues().get(1));
        assertEquals(AHORA.plusHours(24).plusMinutes(30), hasta.getAllValues().get(1));
    }

    @Test
    public void enviarRecordatorios_delegaCadaCitaConSuTipo() {
        Cita c48 = cita();
        Cita c24 = cita();
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any()))
                .thenReturn(List.of(c48))
                .thenReturn(List.of(c24));

        job("simulado", false).enviarRecordatorios();

        verify(recordatorioService).enviarAutomatico(c48.getId(), "RECORDATORIO_48H");
        verify(recordatorioService).enviarAutomatico(c24.getId(), "RECORDATORIO_24H");
    }

    @Test
    public void enviarRecordatorios_excepcionEnUnaCita_noDetieneLasDemas() {
        Cita mala = cita();
        Cita buena = cita();
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any()))
                .thenReturn(List.of(mala, buena))
                .thenReturn(List.of());
        when(recordatorioService.enviarAutomatico(mala.getId(), "RECORDATORIO_48H")).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> job("simulado", false).enviarRecordatorios());

        verify(recordatorioService).enviarAutomatico(buena.getId(), "RECORDATORIO_48H");
    }

    @Test
    public void twilio_sinJobEnabled_noEnviaNada() {
        ReminderJob job = job("twilio", false);
        assertFalse(job.isHabilitado());

        job.enviarRecordatorios();

        verifyNoInteractions(citaRepository, recordatorioService);
    }

    @Test
    public void twilio_conJobEnabled_siEnvia() {
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any())).thenReturn(List.of());
        ReminderJob job = job("twilio", true);
        assertTrue(job.isHabilitado());

        job.enviarRecordatorios();

        verify(citaRepository, times(2)).findByFechaHoraBetweenAndEstadoNot(any(), any(), any());
    }

    @Test
    public void otrosProveedores_siempreHabilitado() {
        assertTrue(job("meta", false).isHabilitado());
        assertTrue(job("simulado", false).isHabilitado());
        assertTrue(job("intoglobe", false).isHabilitado());
    }

    private static Cita cita() {
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        return c;
    }

    @Test
    public void enlace_nuncaEnvia_aunqueHayaCitas() {
        ReminderJob job = job("enlace", true);
        assertFalse(job.isHabilitado());

        job.enviarRecordatorios();

        verifyNoInteractions(citaRepository, recordatorioService);
    }
}
