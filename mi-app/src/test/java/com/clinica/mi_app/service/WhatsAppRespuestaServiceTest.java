package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class WhatsAppRespuestaServiceTest {

    @Mock private CitaRepository citaRepo;
    @Mock private NotificacionRepository notificacionRepo;
    @Mock private WhatsAppMensajeria mensajeria;
    @Mock private AuditClient auditClient;

    private final ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));
    private WhatsAppRespuestaService service;
    private UUID orgId;
    private UUID citaId;
    private static final String TEL = "9511234567";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new WhatsAppRespuestaService(citaRepo, notificacionRepo, mensajeria, auditClient, reloj);
        orgId = UUID.randomUUID();
        citaId = UUID.randomUUID();
        when(mensajeria.proveedor()).thenReturn("simulado");
    }

    @Test
    public void uno_confirmaNotificaYRespondeAlPaciente() {
        Cita cita = cita();

        service.procesarRespuesta(cita, TEL, " 1 ");

        assertEquals("CONFIRMADA", cita.getEstado());
        verify(citaRepo).save(cita);
        verify(mensajeria).enviarTexto(eq(cita.getOrganizacion()), eq(cita), eq(TEL), contains("quedó confirmada"));
        // La confirmación por WhatsApp (webhook) también llega a la campana / Actividad ("Por WhatsApp")
        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertEquals("CONFIRMACION_PACIENTE", captor.getValue().getTipo());
        assertEquals(" 1 ", captor.getValue().getRespuesta());
        assertEquals(cita.getOrganizacion(), captor.getValue().getOrganizacion());
        verify(auditClient).audit(eq("appointment.updated"), eq("CITA"), eq(citaId), eq(orgId), eq(null), anyMap());
    }

    @Test
    public void dos_cancelaCreaNotificacionYResponde() {
        Cita cita = cita();

        service.procesarRespuesta(cita, TEL, "2");

        assertEquals("CANCELADA", cita.getEstado());
        assertEquals("PACIENTE", cita.getCanceladaPor());
        assertEquals(OffsetDateTime.parse("2026-11-10T10:00:00Z"), cita.getCanceladaEn(), "hora de reloj de la clínica");

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        Notificacion n = captor.getValue();
        assertEquals("CANCELACION_PACIENTE", n.getTipo());
        assertEquals("WHATSAPP", n.getCanal());
        assertEquals("2", n.getRespuesta());
        assertFalse(n.getLeida());
        assertNotNull(n.getEnviadaEn());
        assertEquals(cita.getOrganizacion(), n.getOrganizacion());

        verify(mensajeria).enviarTexto(eq(cita.getOrganizacion()), eq(cita), eq(TEL), contains("fue cancelada"));
    }

    @Test
    public void textoDeBoton_confirmar_tambienConfirma() {
        Cita cita = cita();
        service.procesarRespuesta(cita, TEL, "Confirmar");
        assertEquals("CONFIRMADA", cita.getEstado());
    }

    @Test
    public void textoNoReconocido_enviaAyudaSinTocarLaCita() {
        Cita cita = cita();

        service.procesarRespuesta(cita, TEL, "hola");

        assertEquals("SIN_CONFIRMAR", cita.getEstado());
        verify(citaRepo, never()).save(any());
        verify(mensajeria).enviarTexto(any(), eq(cita), eq(TEL), contains("Responde 1 para confirmar"));
    }

    @Test
    public void auditFalla_laCitaYaQuedoGuardada() {
        Cita cita = cita();
        doThrow(new RuntimeException("audit down")).when(auditClient)
                .audit(anyString(), anyString(), any(), any(), any(), anyMap());

        assertDoesNotThrow(() -> service.procesarRespuesta(cita, TEL, "1"));
        verify(citaRepo).save(cita);
    }

    @Test
    public void enlace_confirmar_notificaConfirmacionYRegistraEntrante_sinResponderPorWhatsApp() {
        Cita cita = cita();

        service.confirmarDesdeEnlace(cita);

        assertEquals("CONFIRMADA", cita.getEstado());
        verify(citaRepo).save(cita);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        Notificacion n = captor.getValue();
        assertEquals("CONFIRMACION_PACIENTE", n.getTipo());
        assertEquals("Confirmó desde el enlace", n.getRespuesta());
        assertFalse(n.getLeida());
        assertEquals(cita.getOrganizacion(), n.getOrganizacion());

        verify(mensajeria).registrarEntrante(cita.getOrganizacion(), cita, TEL, "Confirmó desde el enlace", null);
        verify(mensajeria, never()).enviarTexto(any(), any(), any(), any());
        verify(auditClient).audit(eq("appointment.updated"), eq("CITA"), eq(citaId), eq(orgId), eq(null),
                argThat(m -> "enlace".equals(m.get("canal"))));
    }

    @Test
    public void enlace_cancelar_cancelaNotificaYRegistraEntrante() {
        Cita cita = cita();

        service.cancelarDesdeEnlace(cita);

        assertEquals("CANCELADA", cita.getEstado());
        assertEquals("PACIENTE", cita.getCanceladaPor());
        assertEquals(OffsetDateTime.parse("2026-11-10T10:00:00Z"), cita.getCanceladaEn());

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo).save(captor.capture());
        assertEquals("CANCELACION_PACIENTE", captor.getValue().getTipo());
        assertEquals("Canceló desde el enlace", captor.getValue().getRespuesta());

        verify(mensajeria).registrarEntrante(cita.getOrganizacion(), cita, TEL, "Canceló desde el enlace", null);
        verify(mensajeria, never()).enviarTexto(any(), any(), any(), any());
    }

    @Test
    public void enlace_textosDistintosParaConfirmacionYCancelacion() {
        Cita a = cita();
        Cita b = cita();
        service.confirmarDesdeEnlace(a);
        service.cancelarDesdeEnlace(b);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepo, times(2)).save(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getTipo(), captor.getAllValues().get(1).getTipo());
        assertNotEquals(captor.getAllValues().get(0).getRespuesta(), captor.getAllValues().get(1).getRespuesta());
    }

    private Cita cita() {
        Organizacion org = new Organizacion();
        org.setId(orgId);
        Paciente p = new Paciente();
        p.setTelefono(TEL);
        Cita c = new Cita();
        c.setId(citaId);
        c.setOrganizacion(org);
        c.setPaciente(p);
        c.setEstado("SIN_CONFIRMAR");
        c.setFechaHora(OffsetDateTime.parse("2026-11-11T09:00:00Z"));
        return c;
    }
}
