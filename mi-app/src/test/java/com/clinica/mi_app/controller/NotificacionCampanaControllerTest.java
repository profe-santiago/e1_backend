package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.CampanaResponse;
import com.clinica.mi_app.dto.response.NotificacionRecienteResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.clinica.mi_app.controller.AdminDashboardControllerTest.como;
import static com.clinica.mi_app.controller.RecordatorioControllerTest.medico;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificacionController.class)
@Import(SecurityConfig.class)
class NotificacionCampanaControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private NotificacionService service;

    private static final UUID ORG = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void admin_veLaOrganizacionCompleta() throws Exception {
        when(service.campana(ORG, null)).thenReturn(new CampanaResponse(1, List.of(new NotificacionRecienteResponse(
                UUID.randomUUID(), "CANCELACION_PACIENTE", OffsetDateTime.now(), false, UUID.randomUUID(),
                OffsetDateTime.parse("2026-11-11T09:00:00Z"), "Ana Ramírez", "Dra. Mariana López", "2"))));

        mockMvc.perform(get("/api/notificaciones/recientes").with(como("ADMIN", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(1))
                .andExpect(jsonPath("$.items[0].pacienteNombre").value("Ana Ramírez"));
    }

    @Test
    void medico_soloSusCitas() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.campana(ORG, medicoId)).thenReturn(new CampanaResponse(0, List.of()));

        mockMvc.perform(get("/api/notificaciones/recientes").with(medico(ORG, medicoId)))
                .andExpect(status().isOk());
        verify(service).campana(ORG, medicoId);

        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/notificaciones/{id}/leida", id).with(medico(ORG, medicoId)))
                .andExpect(status().isNoContent());
        verify(service).marcarLeida(ORG, medicoId, id);

        when(service.marcarTodasLeidas(ORG, medicoId)).thenReturn(3);
        mockMvc.perform(post("/api/notificaciones/leidas").with(medico(ORG, medicoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(3));
    }

    @Test
    void paciente_403() throws Exception {
        mockMvc.perform(get("/api/notificaciones/recientes").with(como("PACIENTE", ORG)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
