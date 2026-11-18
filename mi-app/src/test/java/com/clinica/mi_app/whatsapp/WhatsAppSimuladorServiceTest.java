package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.WhatsAppMensaje;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** El simulador construye un payload de Meta y lo pasa por el parser real y el mismo processor del webhook. */
class WhatsAppSimuladorServiceTest {

    @Spy private MetaWebhookParser parser = new MetaWebhookParser();
    @Mock private WhatsAppWebhookProcessor processor;
    @Mock private WhatsAppConsultaService consulta;
    private WhatsAppSimuladorService simulador;

    private final UUID org = UUID.randomUUID();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        simulador = new WhatsAppSimuladorService(parser, processor, consulta);
    }

    @Test
    void responder_pasaPorElParserConPayloadDeMetaYElMismoProcessor() {
        WhatsAppMensaje ultimo = new WhatsAppMensaje();
        ultimo.setWamid("wamid.SIM.recordatorio");
        when(consulta.ultimoSaliente(org, "9511234567")).thenReturn(Optional.of(ultimo));

        simulador.responder(org, "951 123 4567", "2");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(parser).mensajes(payload.capture());
        String json = payload.getValue();
        assertTrue(json.contains("\"object\":\"whatsapp_business_account\""));
        assertTrue(json.contains("\"from\":\"529511234567\""));

        ArgumentCaptor<MensajeEntrante> m = ArgumentCaptor.forClass(MensajeEntrante.class);
        verify(processor).procesar(m.capture(), eq(org));
        assertEquals("9511234567", m.getValue().telefono());
        assertEquals("2", m.getValue().texto());
        assertEquals("wamid.SIM.recordatorio", m.getValue().contextoId());
        assertTrue(m.getValue().id().startsWith("wamid.SIM.IN."));
    }

    @Test
    void responder_sinConversacionEnLaOrganizacion_404() {
        when(consulta.ultimoSaliente(any(), anyString())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> simulador.responder(org, "9511234567", "1"));
        verifyNoInteractions(processor);
    }

    @Test
    void responder_telefonoInvalido_400() {
        assertThrows(IllegalArgumentException.class, () -> simulador.responder(org, "123", "1"));
    }

    @Test
    void payload_esParseableComoUnWebhookReal() {
        List<MensajeEntrante> m = new MetaWebhookParser().mensajes(simulador.payloadMeta("9511234567", "hola", "wamid.X"));
        assertEquals(1, m.size());
        assertEquals("hola", m.get(0).texto());
        assertEquals("wamid.X", m.get(0).contextoId());
    }
}
