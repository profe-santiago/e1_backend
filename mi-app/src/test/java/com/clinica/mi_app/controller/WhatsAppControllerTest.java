package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.RecordatorioEstadoResponse;
import com.clinica.mi_app.dto.response.WhatsAppMensajeResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.whatsapp.WhatsAppConsultaService;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import com.clinica.mi_app.whatsapp.WhatsAppSimuladorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.clinica.mi_app.controller.AdminDashboardControllerTest.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WhatsAppController.class)
@Import(SecurityConfig.class)
class WhatsAppControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private WhatsAppConsultaService consulta;
    @MockitoBean private WhatsAppSimuladorService simulador;
    @MockitoBean private WhatsAppMensajeria mensajeria;

    private static final UUID ORG = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");
    private static final String SIMULAR = "{\"telefono\":\"9511234567\",\"texto\":\"1\"}";

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(mensajeria.proveedor()).thenReturn("simulado");
    }

    @Test
    void canal_indicaSiEsSimulado() throws Exception {
        mockMvc.perform(get("/api/whatsapp/canal").with(como("MEDICO", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("simulado"))
                .andExpect(jsonPath("$.simulado").value(true));
    }

    @Test
    void simulador_conProveedorSimulado_usaLaOrganizacionDelJwt() throws Exception {
        when(consulta.mensajes(ORG, null, "9511234567")).thenReturn(List.of(new WhatsAppMensajeResponse(
                UUID.randomUUID(), null, "9511234567", "ENTRANTE", "1", "RECIBIDO", OffsetDateTime.now(), null)));

        mockMvc.perform(post("/api/whatsapp/simulador/responder").with(como("ADMIN", ORG))
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direccion").value("ENTRANTE"));

        verify(simulador).responder(ORG, "9511234567", "1");
    }

    @Test
    void simulador_conOtroProveedor_404() throws Exception {
        when(mensajeria.proveedor()).thenReturn("twilio");

        mockMvc.perform(post("/api/whatsapp/simulador/responder").with(como("ADMIN", ORG))
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULAR))
                .andExpect(status().isNotFound());
        verifyNoInteractions(simulador);
    }

    @Test
    void simulador_textoVacio_400() throws Exception {
        mockMvc.perform(post("/api/whatsapp/simulador/responder").with(como("ADMIN", ORG))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"9511234567\",\"texto\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paciente_403_ySinToken_401() throws Exception {
        mockMvc.perform(post("/api/whatsapp/simulador/responder").with(como("PACIENTE", ORG))
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULAR))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/whatsapp/conversaciones/resumen"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(simulador, consulta);
    }

    @Test
    void conversaciones_yEstado_seAcotanALaOrganizacionDelJwt() throws Exception {
        UUID cita = UUID.randomUUID();
        when(consulta.estadoRecordatorios(eq(ORG), eq(List.of(cita))))
                .thenReturn(Map.of(cita, new RecordatorioEstadoResponse("RESPONDIO", OffsetDateTime.now(), "1")));

        mockMvc.perform(get("/api/whatsapp/conversaciones").param("telefono", "9511234567").with(como("MEDICO", ORG)))
                .andExpect(status().isOk());
        verify(consulta).mensajes(ORG, null, "9511234567");

        mockMvc.perform(get("/api/whatsapp/conversaciones").param("citaId", cita.toString()).with(como("ADMIN", ORG)))
                .andExpect(status().isOk());
        verify(consulta).mensajes(eq(ORG), eq(cita), isNull());

        mockMvc.perform(get("/api/whatsapp/recordatorios/estado").param("citaIds", cita.toString()).with(como("ADMIN", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + cita + "'].estado").value("RESPONDIO"));
    }
}
