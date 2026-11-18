package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.ActividadResponse;
import com.clinica.mi_app.dto.response.ProximoDiaResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.ActividadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.clinica.mi_app.controller.AdminDashboardControllerTest.como;
import static com.clinica.mi_app.controller.RecordatorioControllerTest.medico;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActividadController.class)
@Import(SecurityConfig.class)
class ActividadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private ActividadService service;

    private static final UUID ORG = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");
    private final UUID medicoId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(service.actividad(any(), any(), any()))
                .thenReturn(new ActividadResponse(List.of(), new ActividadResponse.Conteos(0, 0, 0, 0, 0)));
        when(service.proximoDia(any(), any())).thenReturn(ProximoDiaResponse.vacio());
    }

    @Test
    void medico_siempreSuPropioId_aunquePidaOtro() throws Exception {
        UUID ajeno = UUID.randomUUID();
        mockMvc.perform(get("/api/notificaciones/actividad").param("medicoId", ajeno.toString())
                        .param("filtro", "CANCELACIONES").with(medico(ORG, medicoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteos.todas").value(0));
        verify(service).actividad(ORG, medicoId, "CANCELACIONES");

        mockMvc.perform(get("/api/notificaciones/actividad/manana").param("medicoId", ajeno.toString())
                        .with(medico(ORG, medicoId)))
                .andExpect(status().isOk());
        verify(service).proximoDia(ORG, medicoId);

        mockMvc.perform(post("/api/notificaciones/actividad/leidas").param("medicoId", ajeno.toString())
                        .with(medico(ORG, medicoId)))
                .andExpect(status().isOk());
        verify(service).marcarTodasLeidas(ORG, medicoId);
    }

    @Test
    void admin_todaLaClinica_oFiltradaPorMedico() throws Exception {
        mockMvc.perform(get("/api/notificaciones/actividad").with(como("ADMIN", ORG))).andExpect(status().isOk());
        verify(service).actividad(eq(ORG), isNull(), isNull());

        mockMvc.perform(get("/api/notificaciones/actividad").param("medicoId", medicoId.toString())
                        .with(como("ADMIN", ORG)))
                .andExpect(status().isOk());
        verify(service).actividad(ORG, medicoId, null);
    }

    @Test
    void manana_sinCitas_fechaNula() throws Exception {
        mockMvc.perform(get("/api/notificaciones/actividad/manana").with(como("ADMIN", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").doesNotExist())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void paciente_403_sinLogin_401() throws Exception {
        mockMvc.perform(get("/api/notificaciones/actividad").with(como("PACIENTE", ORG)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notificaciones/actividad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
