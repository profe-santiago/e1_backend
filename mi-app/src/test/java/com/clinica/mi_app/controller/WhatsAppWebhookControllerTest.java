package com.clinica.mi_app.controller;

import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.WhatsAppRespuestaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsAppWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhatsAppRespuestaService whatsAppRespuestaService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    public void testRecibirRespuesta_casoFeliz_devuelve200() throws Exception {
        mockMvc.perform(post("/api/whatsapp/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+5211234567890\",\"mensaje\":\"1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testRecibirRespuesta_serviceLanzaExcepcion_devuelve200() throws Exception {
        doThrow(new RuntimeException("boom")).when(whatsAppRespuestaService)
                .procesarRespuesta(anyString(), anyString());

        mockMvc.perform(post("/api/whatsapp/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+5211234567890\",\"mensaje\":\"1\"}"))
                .andExpect(status().isOk());
    }
}
