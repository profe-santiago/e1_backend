package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.client.NotifClient;
import com.clinica.mi_app.model.Cita;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class WhatsAppRespuestaServiceTest {

    @Mock private CitaRepository citaRepo;
    @Mock private NotificacionRepository notificacionRepo;
    @Mock private NotifClient notifClient;
    @Mock private AuditClient auditClient;

    private WhatsAppRespuestaService service;

    private UUID orgId;
    private UUID citaId;
    private static final String TELEFONO = "+5211234567890";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new WhatsAppRespuestaService(citaRepo, notificacionRepo, notifClient, auditClient);
        orgId = UUID.randomUUID();
        citaId = UUID.randomUUID();
    }

    // ── Test 1: "1" confirma ──

    @Test
    public void testProcesarRespuesta_uno_confirmaCita() {
        Cita cita = cita();
        mockBusqueda(Optional.of(cita));
        when(citaRepo.save(any())).thenReturn(cita);

        service.procesarRespuesta(TELEFONO, "1");

        assertEquals("CONFIRMADA", cita.getEstado());
        verify(citaRepo).save(cita);
        verify(auditClient).audit(eq("appointment.updated"), eq("CITA"), eq(citaId), eq(orgId), eq(null), anyMap());
    }

    // ── Test 2: "2" cancela y crea Notificacion ──

    @Test
    public void testProcesarRespuesta_dos_cancelaCitaYCreaNotificacion() {
        Cita cita = cita();
        mockBusqueda(Optional.of(cita));
        when(citaRepo.save(any())).thenReturn(cita);

        service.procesarRespuesta(TELEFONO, "2");

        assertEquals("CANCELADA", cita.getEstado());
        assertEquals("PACIENTE", cita.getCanceladaPor());
        assertNotNull(cita.getCanceladaEn());
        verify(citaRepo).save(cita);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        Notificacion notif = captor.getValue();
        assertEquals("CANCELACION_PACIENTE", notif.getTipo());
        assertEquals("WHATSAPP", notif.getCanal());
        assertEquals("ENVIADO", notif.getEstado());
        assertEquals("2", notif.getRespuesta());
        assertEquals(cita, notif.getCita());
        assertEquals(cita.getOrganizacion(), notif.getOrganizacion());
    }

    // ── Test 3: texto no reconocido ──

    @Test
    public void testProcesarRespuesta_textoNoReconocido_enviaAyudaYNoModificaCita() {
        Cita cita = cita();
        mockBusqueda(Optional.of(cita));

        service.procesarRespuesta(TELEFONO, "hola");

        verify(notifClient).sendNotification(eq("WHATSAPP"), eq(TELEFONO), anyString(), eq(orgId));
        verify(citaRepo, never()).save(any());
        assertEquals("SIN_CONFIRMAR", cita.getEstado());
    }

    // ── Test 4: sin cita encontrada ──

    @Test
    public void testProcesarRespuesta_sinCitaEncontrada_noHaceNada() {
        mockBusqueda(Optional.empty());

        assertDoesNotThrow(() -> service.procesarRespuesta(TELEFONO, "1"));

        verify(citaRepo, never()).save(any());
        verifyNoInteractions(notificacionRepo, notifClient, auditClient);
    }

    // ── Test 5: excepción de audit no impide que la cita ya se haya guardado ──

    @Test
    public void testProcesarRespuesta_auditLanzaExcepcion_citaYaGuardada() {
        Cita cita = cita();
        mockBusqueda(Optional.of(cita));
        when(citaRepo.save(any())).thenReturn(cita);
        doThrow(new RuntimeException("audit down")).when(auditClient)
                .audit(anyString(), anyString(), any(), any(), any(), anyMap());

        assertDoesNotThrow(() -> service.procesarRespuesta(TELEFONO, "1"));

        verify(citaRepo).save(cita);
    }

    // ── helpers ──

    private void mockBusqueda(Optional<Cita> resultado) {
        when(citaRepo.findFirstByPaciente_TelefonoAndEstadoAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                eq(TELEFONO), eq("SIN_CONFIRMAR"), any(OffsetDateTime.class)))
                .thenReturn(resultado);
    }

    private Cita cita() {
        Organizacion org = new Organizacion();
        org.setId(orgId);

        Paciente paciente = new Paciente();
        paciente.setTelefono(TELEFONO);

        Cita c = new Cita();
        c.setId(citaId);
        c.setOrganizacion(org);
        c.setPaciente(paciente);
        c.setEstado("SIN_CONFIRMAR");
        c.setFechaHora(OffsetDateTime.now().plusDays(1));
        return c;
    }
}
