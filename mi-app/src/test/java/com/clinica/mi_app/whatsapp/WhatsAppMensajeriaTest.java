package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WhatsAppMensajeriaTest {

    @Mock private WhatsAppSender sender;
    @Mock private WhatsAppMensajeRepository repo;
    private WhatsAppMensajeria mensajeria;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(sender.proveedor()).thenReturn("twilio");
        mensajeria = new WhatsAppMensajeria(sender, repo, "recordatorio_cita");
    }

    @Test
    void recordatorio_registraSalienteConTextoRenderizado() {
        Cita cita = cita();
        when(sender.sendTemplate(eq(cita.getOrganizacion().getId()), eq("9511234567"), eq("recordatorio_cita"), any()))
                .thenReturn(Optional.of("SM1"));

        assertEquals(Optional.of("SM1"), mensajeria.enviarRecordatorio(cita));

        WhatsAppMensaje m = guardado();
        assertEquals("SALIENTE", m.getDireccion());
        assertEquals("ENVIADO", m.getEstado());
        assertEquals("SM1", m.getWamid());
        assertEquals("9511234567", m.getTelefono(), "se guarda en 10 dígitos aunque el paciente tenga +52 1");
        assertSame(cita, m.getCita());
        assertTrue(m.getTexto().startsWith("Hola Ana Ramírez, te recordamos tu cita con Dra. Mariana López"));
    }

    @Test
    void senderFallaOLanza_registraFallidoSinPropagar() {
        Cita cita = cita();
        when(sender.sendTemplate(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertTrue(assertDoesNotThrow(() -> mensajeria.enviarRecordatorio(cita)).isEmpty());
        WhatsAppMensaje m = guardado();
        assertEquals("FALLIDO", m.getEstado());
        assertNull(m.getWamid());
    }

    @Test
    void actualizarEstado_soloAvanza_oFallido() {
        WhatsAppMensaje m = new WhatsAppMensaje();
        m.setDireccion("SALIENTE");
        m.setEstado("LEIDO");
        when(repo.findFirstByWamid("SM1")).thenReturn(Optional.of(m));

        mensajeria.actualizarEstado("SM1", "ENTREGADO");
        assertEquals("LEIDO", m.getEstado(), "un callback atrasado no retrocede el estado");
        verify(repo, never()).save(any());

        mensajeria.actualizarEstado("SM1", "FALLIDO");
        assertEquals("FALLIDO", m.getEstado());
    }

    private WhatsAppMensaje guardado() {
        ArgumentCaptor<WhatsAppMensaje> c = ArgumentCaptor.forClass(WhatsAppMensaje.class);
        verify(repo).save(c.capture());
        return c.getValue();
    }

    private static Cita cita() {
        Organizacion o = new Organizacion();
        o.setId(UUID.randomUUID());
        Paciente p = new Paciente();
        p.setNombre("Ana Ramírez");
        p.setTelefono("+52 1 951 123 4567");
        Medico m = new Medico();
        m.setNombre("Dra. Mariana López");
        Consultorio c = new Consultorio();
        c.setNombre("Consultorio 1");
        Cita cita = new Cita();
        cita.setId(UUID.randomUUID());
        cita.setOrganizacion(o);
        cita.setPaciente(p);
        cita.setMedico(m);
        cita.setConsultorio(c);
        cita.setFechaHora(OffsetDateTime.parse("2026-11-11T09:00:00Z"));
        return cita;
    }
}
