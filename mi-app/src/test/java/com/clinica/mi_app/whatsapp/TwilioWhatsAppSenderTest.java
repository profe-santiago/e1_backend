package com.clinica.mi_app.whatsapp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Servidor HTTP local que imita la API de Twilio: se verifica la petición exacta que sale. */
class TwilioWhatsAppSenderTest {

    private static final String SID = "AC00000000000000000000000000000000";
    private static final String TOKEN = "token-de-prueba";

    private HttpServer server;
    private volatile String metodo;
    private volatile String ruta;
    private volatile String authorization;
    private volatile String contentType;
    private volatile String cuerpo;
    private volatile int status = 201;
    private volatile String respuesta = "{\"sid\":\"SM0123456789abcdef0123456789abcdef\",\"status\":\"queued\"}";

    @BeforeEach
    void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            metodo = ex.getRequestMethod();
            ruta = ex.getRequestURI().getPath();
            authorization = ex.getRequestHeaders().getFirst("Authorization");
            contentType = ex.getRequestHeaders().getFirst("Content-Type");
            cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = respuesta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void detener() {
        server.stop(0);
    }

    private TwilioWhatsAppSender sender(String sid, String token, String webhookUrl) {
        return new TwilioWhatsAppSender(sid, token, "whatsapp:+17372508034", webhookUrl,
                "http://127.0.0.1:" + server.getAddress().getPort(), new StaticListableBeanFactory().getBeanProvider(
                        io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.class));
    }

    @Test
    void sendText_formUrlencodedConBasicAuthYDevuelveSid() {
        Optional<String> sid = sender(SID, TOKEN, "").sendText(UUID.randomUUID(), "9511234567", "Hola ¿cómo estás?");

        assertEquals(Optional.of("SM0123456789abcdef0123456789abcdef"), sid);
        assertEquals("POST", metodo);
        assertEquals("/2010-04-01/Accounts/" + SID + "/Messages.json", ruta);
        assertTrue(contentType.startsWith("application/x-www-form-urlencoded"));
        String esperado = "Basic " + Base64.getEncoder().encodeToString((SID + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
        assertEquals(esperado, authorization);

        Map<String, String> form = form(cuerpo);
        assertEquals("whatsapp:+17372508034", form.get("From"));
        assertEquals("whatsapp:+529511234567", form.get("To"));
        assertEquals("Hola ¿cómo estás?", form.get("Body"));
        assertFalse(form.containsKey("StatusCallback"), "sin webhook-url no se pide status callback");
    }

    @Test
    void sendTemplate_mandaElTextoRenderizadoYStatusCallback() {
        List<String> params = List.of("Ana Ramírez", "Dra. Mariana López", "martes 11 de noviembre", "9:00 a.m.", "Consultorio 1");

        sender(SID, TOKEN, "https://demo.trycloudflare.com/api/whatsapp/twilio")
                .sendTemplate(UUID.randomUUID(), "+52 1 951 123 4567", "recordatorio_cita", params);

        Map<String, String> form = form(cuerpo);
        assertEquals("whatsapp:+529511234567", form.get("To"), "normaliza 521 + 10 dígitos");
        assertEquals(RecordatorioPlantilla.renderizar(params), form.get("Body"));
        assertTrue(form.get("Body").endsWith("Responde 1 para confirmar o 2 para cancelar."));
        assertEquals("https://demo.trycloudflare.com/api/whatsapp/twilio/status", form.get("StatusCallback"));
    }

    @Test
    void errorHttp_devuelveVacioSinExcepcion() {
        status = 401;
        respuesta = "{\"code\":20003,\"message\":\"Authenticate\"}";

        Optional<String> r = assertDoesNotThrow(() -> sender(SID, TOKEN, "").sendText(null, "9511234567", "x"));
        assertTrue(r.isEmpty());
    }

    @Test
    void servidorCaido_devuelveVacioSinExcepcion() {
        TwilioWhatsAppSender s = sender(SID, TOKEN, "");
        server.stop(0);

        assertTrue(assertDoesNotThrow(() -> s.sendText(null, "9511234567", "x")).isEmpty());
    }

    @Test
    void sinCredenciales_noLlamaAlProveedor() {
        assertTrue(sender("", "", "").sendText(null, "9511234567", "x").isEmpty());
        assertNull(metodo);
    }

    @Test
    void telefonoInvalido_noLlamaAlProveedor() {
        assertTrue(sender(SID, TOKEN, "").sendText(null, "12345", "x").isEmpty());
        assertNull(metodo);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String par : body.split("&")) {
            String[] kv = par.split("=", 2);
            m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
}
