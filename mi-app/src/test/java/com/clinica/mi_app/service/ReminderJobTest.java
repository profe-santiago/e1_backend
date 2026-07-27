package com.clinica.mi_app.service;

import com.clinica.mi_app.client.NotifClient;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ReminderJobTest {

    @Mock private CitaRepository citaRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private NotifClient notifClient;

    private ReminderJob job;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        job = new ReminderJob(citaRepository, notificacionRepository, notifClient);
    }

    // ── Test 1: sin recordatorio previo + NotifClient exitoso → guarda Notificacion ENVIADO ──

    @Test
    public void procesarCita_sinRecordatorioPrevio_guardaNotificacion() {
        Cita cita = citaEjemplo();
        when(notificacionRepository.findByCitaId(cita.getId())).thenReturn(List.of());
        when(notifClient.sendTemplate(anyString(), anyMap(), anyString(), any(UUID.class)))
                .thenReturn(Optional.of("ok"));

        job.procesarCita(cita, "RECORDATORIO_48H");

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        Notificacion guardada = captor.getValue();
        assertEquals("RECORDATORIO_48H", guardada.getTipo());
        assertEquals("ENVIADO", guardada.getEstado());
        assertNotNull(guardada.getEnviadaEn());
        assertEquals(cita, guardada.getCita());
    }

    // ── Test 2: ya existe Notificacion ENVIADO para ese tipo → sendTemplate no se llama ──

    @Test
    public void procesarCita_recordatorioYaEnviado_noLlamaSendTemplate() {
        Cita cita = citaEjemplo();
        Notificacion ya = new Notificacion();
        ya.setTipo("RECORDATORIO_48H");
        ya.setEstado("ENVIADO");
        when(notificacionRepository.findByCitaId(cita.getId())).thenReturn(List.of(ya));

        job.procesarCita(cita, "RECORDATORIO_48H");

        verifyNoInteractions(notifClient);
        verify(notificacionRepository, never()).save(any());
    }

    // ── Test 3: NotifClient devuelve Optional.empty() → no se guarda Notificacion ──

    @Test
    public void procesarCita_notifClientVacio_noGuardaNotificacion() {
        Cita cita = citaEjemplo();
        when(notificacionRepository.findByCitaId(cita.getId())).thenReturn(List.of());
        when(notifClient.sendTemplate(anyString(), anyMap(), anyString(), any(UUID.class)))
                .thenReturn(Optional.empty());

        job.procesarCita(cita, "RECORDATORIO_24H");

        verify(notificacionRepository, never()).save(any());
    }

    // ── Test 4: excepción en una cita no detiene el procesamiento de las demás ──

    @Test
    public void enviarRecordatorios_excepcionEnUnaCita_noDetieneLasDemas() {
        Cita citaMala = citaEjemplo();
        Cita citaBuena = citaEjemplo();

        // Primera llamada (48h) devuelve las dos citas; segunda llamada (24h) devuelve vacío
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any()))
                .thenReturn(List.of(citaMala, citaBuena))
                .thenReturn(List.of());

        when(notificacionRepository.findByCitaId(any())).thenReturn(List.of());
        // Primera cita lanza excepción; segunda tiene éxito
        when(notifClient.sendTemplate(anyString(), anyMap(), anyString(), any(UUID.class)))
                .thenThrow(new RuntimeException("fallo de red"))
                .thenReturn(Optional.of("ok"));

        assertDoesNotThrow(() -> job.enviarRecordatorios());

        // La cita buena debe haber generado un save a pesar del fallo anterior
        verify(notificacionRepository, times(1)).save(any(Notificacion.class));
    }

    // ── Test 5: ventanas de tiempo correctas ──

    @Test
    public void enviarRecordatorios_ventanasDeTiempoCorrectas() {
        when(citaRepository.findByFechaHoraBetweenAndEstadoNot(any(), any(), any()))
                .thenReturn(List.of());

        OffsetDateTime antes = OffsetDateTime.now();
        job.enviarRecordatorios();
        OffsetDateTime despues = OffsetDateTime.now();

        ArgumentCaptor<OffsetDateTime> desdeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hastaCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<String> estadoCaptor = ArgumentCaptor.forClass(String.class);

        verify(citaRepository, times(2)).findByFechaHoraBetweenAndEstadoNot(
                desdeCaptor.capture(), hastaCaptor.capture(), estadoCaptor.capture());

        List<OffsetDateTime> desdes = desdeCaptor.getAllValues();
        List<OffsetDateTime> hastas = hastaCaptor.getAllValues();
        List<String> estados = estadoCaptor.getAllValues();

        // Tolerancia generosa para absorber tiempo de ejecución del test y granularidad del clock
        Duration tol = Duration.ofSeconds(2);

        // Ventana 48h: desde ≈ ahora+47h30m, hasta ≈ ahora+48h30m
        OffsetDateTime desde48 = desdes.get(0);
        OffsetDateTime hasta48 = hastas.get(0);
        assertFalse(desde48.isBefore(antes.plusHours(47).plusMinutes(30).minus(tol)),
                "desde48 demasiado temprano");
        assertFalse(desde48.isAfter(despues.plusHours(47).plusMinutes(30).plus(tol)),
                "desde48 demasiado tarde");
        assertFalse(hasta48.isBefore(antes.plusHours(48).plusMinutes(30).minus(tol)),
                "hasta48 demasiado temprano");
        assertFalse(hasta48.isAfter(despues.plusHours(48).plusMinutes(30).plus(tol)),
                "hasta48 demasiado tarde");

        // Ventana 24h: desde ≈ ahora+23h30m, hasta ≈ ahora+24h30m
        OffsetDateTime desde24 = desdes.get(1);
        OffsetDateTime hasta24 = hastas.get(1);
        assertFalse(desde24.isBefore(antes.plusHours(23).plusMinutes(30).minus(tol)),
                "desde24 demasiado temprano");
        assertFalse(desde24.isAfter(despues.plusHours(23).plusMinutes(30).plus(tol)),
                "desde24 demasiado tarde");
        assertFalse(hasta24.isBefore(antes.plusHours(24).plusMinutes(30).minus(tol)),
                "hasta24 demasiado temprano");
        assertFalse(hasta24.isAfter(despues.plusHours(24).plusMinutes(30).plus(tol)),
                "hasta24 demasiado tarde");

        // Ambas llamadas excluyen "CANCELADA"
        assertEquals("CANCELADA", estados.get(0));
        assertEquals("CANCELADA", estados.get(1));
    }

    // ── helpers ──

    private Cita citaEjemplo() {
        Organizacion org = new Organizacion();
        org.setId(UUID.randomUUID());

        Paciente paciente = new Paciente();
        paciente.setNombre("Ana López");
        paciente.setTelefono("+521234567890");

        Medico medico = new Medico();
        medico.setNombre("Dr. García");

        Consultorio consultorio = new Consultorio();
        consultorio.setNombre("Consultorio 1");

        Cita cita = new Cita();
        cita.setId(UUID.randomUUID());
        cita.setOrganizacion(org);
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setConsultorio(consultorio);
        cita.setFechaHora(OffsetDateTime.now().plusHours(48));
        return cita;
    }
}
