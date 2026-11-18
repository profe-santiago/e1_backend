package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import com.clinica.mi_app.service.WhatsAppRespuestaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.clinica.mi_app.whatsapp.WhatsAppWebhookProcessor.Resultado.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class WhatsAppWebhookProcessorTest {

    @Mock private WhatsAppMensajeRepository repo;
    @Mock private WhatsAppMensajeria mensajeria;
    @Mock private WhatsAppRespuestaService respuestaService;

    /** ahora = 2026-11-10T10:00Z (reloj de la clínica en UTC) */
    private final ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));
    private WhatsAppWebhookProcessor processor;

    private static final String TEL = "9511234567";
    private final Organizacion orgA = org();
    private final Organizacion orgB = org();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        processor = new WhatsAppWebhookProcessor(repo, mensajeria, respuestaService, reloj);
    }

    @Test
    void respuestaSinContexto_resuelvePorUltimoRecordatorioPendiente() {
        Cita confirmada = cita(orgA, "CONFIRMADA", "2026-11-12T09:00:00Z");
        Cita pendiente = cita(orgA, "SIN_CONFIRMAR", "2026-11-11T09:00:00Z");
        when(repo.findByTelefonoAndDireccionOrderByCreatedAtDesc(TEL, "SALIENTE"))
                .thenReturn(List.of(saliente(confirmada, "SM2"), saliente(pendiente, "SM1")));

        var r = processor.procesar(new MensajeEntrante("SMIN1", TEL, "1", null), null);

        assertEquals(PROCESADO, r);
        verify(mensajeria).registrarEntrante(orgA, pendiente, TEL, "1", "SMIN1");
        verify(respuestaService).procesarRespuesta(pendiente, TEL, "1");
    }

    @Test
    void flujo2_cancelaLaCitaCitadaPorContexto() {
        Cita citada = cita(orgA, "SIN_CONFIRMAR", "2026-11-12T09:00:00Z");
        Cita otra = cita(orgA, "SIN_CONFIRMAR", "2026-11-11T09:00:00Z");
        when(repo.findFirstByWamid("wamid.OUT.citada")).thenReturn(Optional.of(saliente(citada, "wamid.OUT.citada")));
        when(repo.findByTelefonoAndDireccionOrderByCreatedAtDesc(TEL, "SALIENTE"))
                .thenReturn(List.of(saliente(otra, "wamid.OUT.otra")));

        processor.procesar(new MensajeEntrante("wamid.IN", TEL, "2", "wamid.OUT.citada"), null);

        verify(respuestaService).procesarRespuesta(citada, TEL, "2");
    }

    @Test
    void citaPasadaONoPendiente_soloRegistraElEntrante() {
        Cita pasada = cita(orgA, "SIN_CONFIRMAR", "2026-11-10T09:00:00Z"); // 09:00 < ahora 10:00
        when(repo.findByTelefonoAndDireccionOrderByCreatedAtDesc(TEL, "SALIENTE")).thenReturn(List.of(saliente(pasada, "SM1")));

        var r = processor.procesar(new MensajeEntrante("SMIN", TEL, "1", null), null);

        assertEquals(SIN_CITA_PENDIENTE, r);
        verify(mensajeria).registrarEntrante(orgA, pasada, TEL, "1", "SMIN");
        verifyNoInteractions(respuestaService);
    }

    @Test
    void duplicado_noSeProcesaDosVeces() {
        when(repo.existsByWamid("SMIN")).thenReturn(true);

        assertEquals(DUPLICADO, processor.procesar(new MensajeEntrante("SMIN", TEL, "1", null), null));
        verifyNoInteractions(mensajeria, respuestaService);
    }

    @Test
    void telefonoSinConversacion_seIgnora() {
        when(repo.findByTelefonoAndDireccionOrderByCreatedAtDesc(TEL, "SALIENTE")).thenReturn(List.of());

        assertEquals(SIN_CONVERSACION, processor.procesar(new MensajeEntrante("SMIN", TEL, "1", null), null));
        verify(mensajeria, never()).registrarEntrante(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void telefonoNoReconocido_seIgnora() {
        assertEquals(IGNORADO, processor.procesar(new MensajeEntrante("SMIN", null, "1", null), null));
        verifyNoInteractions(repo, mensajeria, respuestaService);
    }

    @Test
    void conOrganizacion_soloBuscaEnEsaOrganizacion_eIgnoraContextoAjeno() {
        Cita deB = cita(orgB, "SIN_CONFIRMAR", "2026-11-11T09:00:00Z");
        Cita deA = cita(orgA, "SIN_CONFIRMAR", "2026-11-11T11:00:00Z");
        when(repo.findFirstByWamid("wamid.B")).thenReturn(Optional.of(saliente(deB, "wamid.B")));
        when(repo.findByOrganizacionIdAndTelefonoAndDireccionOrderByCreatedAtDesc(orgA.getId(), TEL, "SALIENTE"))
                .thenReturn(List.of(saliente(deA, "wamid.A")));

        processor.procesar(new MensajeEntrante("wamid.IN", TEL, "1", "wamid.B"), orgA.getId());

        verify(respuestaService).procesarRespuesta(deA, TEL, "1");
        verify(respuestaService, never()).procesarRespuesta(eq(deB), anyString(), anyString());
        verify(repo, never()).findByTelefonoAndDireccionOrderByCreatedAtDesc(anyString(), anyString());
    }

    @Test
    void aplicarEstado_delegaEnLaFachada() {
        processor.aplicarEstado(new EstadoEntrega("SM1", "ENTREGADO"));
        verify(mensajeria).actualizarEstado("SM1", "ENTREGADO");
        processor.aplicarEstado(null);
        verify(mensajeria, times(1)).actualizarEstado(any(), any());
        verify(mensajeria, never()).actualizarEstado(isNull(), isNull());
    }

    private static Organizacion org() {
        Organizacion o = new Organizacion();
        o.setId(UUID.randomUUID());
        return o;
    }

    private static Cita cita(Organizacion org, String estado, String fechaHora) {
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(org);
        c.setEstado(estado);
        c.setFechaHora(OffsetDateTime.parse(fechaHora));
        return c;
    }

    private static WhatsAppMensaje saliente(Cita cita, String wamid) {
        WhatsAppMensaje m = new WhatsAppMensaje();
        m.setOrganizacion(cita.getOrganizacion());
        m.setCita(cita);
        m.setTelefono(TEL);
        m.setDireccion("SALIENTE");
        m.setWamid(wamid);
        m.setEstado("ENVIADO");
        return m;
    }
}
