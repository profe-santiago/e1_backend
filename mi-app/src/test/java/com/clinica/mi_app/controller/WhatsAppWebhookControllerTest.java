package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.whatsapp.EstadoEntrega;
import com.clinica.mi_app.whatsapp.MensajeEntrante;
import com.clinica.mi_app.whatsapp.TwilioWebhook;
import com.clinica.mi_app.whatsapp.WhatsAppWebhookProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Webhook de Twilio con la seguridad real: es público (sin JWT) y se autentica con X-Twilio-Signature. */
@WebMvcTest(controllers = WhatsAppWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "notif.provider=twilio",
        "whatsapp.twilio.auth-token=token-de-prueba",
        "whatsapp.twilio.webhook-url=https://demo.trycloudflare.com/api/whatsapp/twilio"
})
class WhatsAppWebhookControllerTest {

    private static final String URL = "https://demo.trycloudflare.com/api/whatsapp/twilio";
    private static final String TOKEN = "token-de-prueba";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private WhatsAppWebhookProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private static Map<String, List<String>> mensaje(String body) {
        Map<String, List<String>> p = new LinkedHashMap<>();
        p.put("MessageSid", List.of("SM0001"));
        p.put("AccountSid", List.of("AC0001"));
        p.put("From", List.of("whatsapp:+5219511234567"));
        p.put("To", List.of("whatsapp:+17372508034"));
        p.put("Body", List.of(body));
        p.put("NumMedia", List.of("0"));
        return p;
    }

    private static MockHttpServletRequestBuilder form(String path, Map<String, List<String>> params) {
        MockHttpServletRequestBuilder b = post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach((k, v) -> b.param(k, v.toArray(String[]::new)));
        return b;
    }

    @Test
    void firmaValida_sinJwt_procesaYRespondeTwimlVacio() throws Exception {
        Map<String, List<String>> p = mensaje("1");

        mockMvc.perform(form("/api/whatsapp/twilio", p).header("X-Twilio-Signature", TwilioWebhook.firmar(URL, p, TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(content().string(WhatsAppWebhookController.TWIML_VACIO));

        verify(processor).procesar(eq(new MensajeEntrante("SM0001", "9511234567", "1", null)), isNull());
    }

    @Test
    void flujo2_tambienPasaPorElProcessor() throws Exception {
        Map<String, List<String>> p = mensaje("2");

        mockMvc.perform(form("/api/whatsapp/twilio", p).header("X-Twilio-Signature", TwilioWebhook.firmar(URL, p, TOKEN)))
                .andExpect(status().isOk());

        verify(processor).procesar(eq(new MensajeEntrante("SM0001", "9511234567", "2", null)), isNull());
    }

    @Test
    void firmaInvalida_403() throws Exception {
        mockMvc.perform(form("/api/whatsapp/twilio", mensaje("1")).header("X-Twilio-Signature", "firma-falsa="))
                .andExpect(status().isForbidden());
        verifyNoInteractions(processor);
    }

    @Test
    void sinFirma_403() throws Exception {
        mockMvc.perform(form("/api/whatsapp/twilio", mensaje("1")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(processor);
    }

    @Test
    void parametrosAlteradosTrasFirmar_403() throws Exception {
        String firma = TwilioWebhook.firmar(URL, mensaje("1"), TOKEN);
        mockMvc.perform(form("/api/whatsapp/twilio", mensaje("2")).header("X-Twilio-Signature", firma))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorAlProcesar_igualResponde200() throws Exception {
        doThrow(new RuntimeException("boom")).when(processor).procesar(any(), any());
        Map<String, List<String>> p = mensaje("1");

        mockMvc.perform(form("/api/whatsapp/twilio", p).header("X-Twilio-Signature", TwilioWebhook.firmar(URL, p, TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void statusCallback_firmadoConUrlStatus() throws Exception {
        Map<String, List<String>> p = new LinkedHashMap<>();
        p.put("MessageSid", List.of("SM0009"));
        p.put("MessageStatus", List.of("read"));

        mockMvc.perform(form("/api/whatsapp/twilio/status", p)
                        .header("X-Twilio-Signature", TwilioWebhook.firmar(URL + "/status", p, TOKEN)))
                .andExpect(status().isOk());
        verify(processor).aplicarEstado(new EstadoEntrega("SM0009", "LEIDO"));

        mockMvc.perform(form("/api/whatsapp/twilio/status", p)
                        .header("X-Twilio-Signature", TwilioWebhook.firmar(URL, p, TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void conOtroProveedor_404() throws Exception {
        WhatsAppWebhookProcessor otro = mock(WhatsAppWebhookProcessor.class);
        MockMvc simulado = MockMvcBuilders.standaloneSetup(
                new WhatsAppWebhookController(otro, "simulado", TOKEN, URL)).build();
        Map<String, List<String>> p = mensaje("1");

        simulado.perform(form("/api/whatsapp/twilio", p).header("X-Twilio-Signature", TwilioWebhook.firmar(URL, p, TOKEN)))
                .andExpect(status().isNotFound());
        simulado.perform(form("/api/whatsapp/twilio/status", p))
                .andExpect(status().isNotFound());
        verifyNoInteractions(otro);
    }
}
