package com.clinica.mi_app.whatsapp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TwilioWebhookTest {

    /**
     * Vector de la documentación de Twilio ("Validating Signatures from Twilio"), recalculado de forma
     * independiente con: printf '%s' "$URL$PARAMS" | openssl dgst -sha1 -hmac 12345 -binary | base64
     */
    private static final String URL = "https://mycompany.com/myapp.php?foo=1&bar=2";
    private static final String TOKEN = "12345";
    private static final String FIRMA = "0/KCTR6DLpKmkAf8muzZqo1nDgQ=";

    private static Map<String, List<String>> paramsDoc() {
        Map<String, List<String>> p = new LinkedHashMap<>();
        // desordenados a propósito: la firma ordena por nombre
        p.put("To", List.of("+18005551212"));
        p.put("CallSid", List.of("CA1234567890ABCDE"));
        p.put("From", List.of("+12349013030"));
        p.put("Digits", List.of("1234"));
        p.put("Caller", List.of("+12349013030"));
        return p;
    }

    @Test
    void firmar_vectorConocido() {
        assertEquals(FIRMA, TwilioWebhook.firmar(URL, paramsDoc(), TOKEN));
    }

    @Test
    void firmaValida_aceptaLaCorrecta() {
        assertTrue(TwilioWebhook.firmaValida(URL, paramsDoc(), TOKEN, FIRMA));
    }

    @Test
    void firmaValida_rechazaFirmaUrlTokenOParametrosAlterados() {
        assertFalse(TwilioWebhook.firmaValida(URL, paramsDoc(), TOKEN, "0/KCTR6DLpKmkAf8muzZqo1nDgX="));
        assertFalse(TwilioWebhook.firmaValida(URL + "&x=1", paramsDoc(), TOKEN, FIRMA));
        assertFalse(TwilioWebhook.firmaValida(URL, paramsDoc(), "otro-token", FIRMA));
        Map<String, List<String>> alterados = paramsDoc();
        alterados.put("Digits", List.of("9999"));
        assertFalse(TwilioWebhook.firmaValida(URL, alterados, TOKEN, FIRMA));
    }

    @Test
    void firmaValida_fallaCerradoSinConfiguracionOSinFirma() {
        assertFalse(TwilioWebhook.firmaValida("", paramsDoc(), TOKEN, FIRMA));
        assertFalse(TwilioWebhook.firmaValida(URL, paramsDoc(), "", FIRMA));
        assertFalse(TwilioWebhook.firmaValida(URL, paramsDoc(), TOKEN, null));
    }

    @Test
    void mensaje_normalizaFromYUsaBody() {
        Map<String, List<String>> p = new LinkedHashMap<>();
        p.put("MessageSid", List.of("SM111"));
        p.put("From", List.of("whatsapp:+5219511234567"));
        p.put("Body", List.of("1"));

        MensajeEntrante m = TwilioWebhook.mensaje(p);

        assertEquals("SM111", m.id());
        assertEquals("9511234567", m.telefono());
        assertEquals("1", m.texto());
        assertNull(m.contextoId());
    }

    @Test
    void mensaje_botonYRespuestaCitada() {
        Map<String, List<String>> p = new LinkedHashMap<>();
        p.put("MessageSid", List.of("SM222"));
        p.put("From", List.of("whatsapp:+529511234567"));
        p.put("Body", List.of("Cancelar"));
        p.put("ButtonPayload", List.of("2"));
        p.put("OriginalRepliedMessageSid", List.of("SM000"));

        MensajeEntrante m = TwilioWebhook.mensaje(p);

        assertEquals("9511234567", m.telefono());
        assertEquals("2", m.texto(), "el payload del botón tiene prioridad sobre Body");
        assertEquals("SM000", m.contextoId());
    }

    @Test
    void estado_traduceMessageStatus() {
        assertEquals(new EstadoEntrega("SM1", "ENTREGADO"),
                TwilioWebhook.estado(Map.of("MessageSid", List.of("SM1"), "MessageStatus", List.of("delivered"))));
        assertEquals("LEIDO", TwilioWebhook.estado(Map.of("MessageSid", List.of("SM1"), "MessageStatus", List.of("read"))).estado());
        assertEquals("FALLIDO", TwilioWebhook.estado(Map.of("MessageSid", List.of("SM1"), "MessageStatus", List.of("undelivered"))).estado());
    }
}
