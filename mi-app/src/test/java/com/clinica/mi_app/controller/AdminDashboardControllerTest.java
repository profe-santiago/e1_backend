package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.AdminDashboardResponse;
import com.clinica.mi_app.dto.response.CitaResumenResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDashboardController.class)
@Import(SecurityConfig.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtFilter jwtFilter;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminDashboardService service;

    private static final UUID ORG_ID = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            var chain = invocation.getArgument(2, jakarta.servlet.FilterChain.class);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    /** Autenticación como la deja JwtFilter: rol como authority y claims en details. */
    static RequestPostProcessor como(String rol, UUID orgId) {
        var auth = new UsernamePasswordAuthenticationToken("user@clinica.mx", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        auth.setDetails(Map.of("rol", rol, "organizacionId", orgId, "userId", UUID.randomUUID()));
        return authentication(auth);
    }

    @Test
    void dashboard_admin_usaOrganizacionDelJwt() throws Exception {
        Map<String, Long> estados = new LinkedHashMap<>();
        estados.put("SIN_CONFIRMAR", 1L);
        estados.put("CONFIRMADA", 2L);
        var proxima = new CitaResumenResponse(UUID.randomUUID(), OffsetDateTime.parse("2026-11-10T11:00:00Z"),
                "CONFIRMADA", UUID.randomUUID(), "Ana Ramírez", "5512345678", UUID.randomUUID(), "Dra. Mariana López");
        when(service.dashboard(ORG_ID)).thenReturn(new AdminDashboardResponse(
                LocalDate.of(2026, 11, 10), 3, estados, new BigDecimal("1300.00"), 2, new BigDecimal("1150.00"),
                new AdminDashboardResponse.Tasas(75.0, 25.0, 4), List.of(proxima), List.of()));

        mockMvc.perform(get("/api/admin/dashboard").with(como("ADMIN", ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-11-10"))
                .andExpect(jsonPath("$.citasHoyTotal").value(3))
                .andExpect(jsonPath("$.citasHoyPorEstado.CONFIRMADA").value(2))
                .andExpect(jsonPath("$.ingresosHoy").value(1300.00))
                .andExpect(jsonPath("$.cobrosPendientesCantidad").value(2))
                .andExpect(jsonPath("$.tasas30Dias.confirmacion").value(75.0))
                .andExpect(jsonPath("$.proximasCitas[0].pacienteNombre").value("Ana Ramírez"));

        verify(service).dashboard(ORG_ID);
    }

    @Test
    void dashboard_medico_403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(como("MEDICO", ORG_ID)))
                .andExpect(status().isForbidden());
        verify(service, never()).dashboard(any());
    }

    @Test
    void dashboard_paciente_403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(como("PACIENTE", ORG_ID)))
                .andExpect(status().isForbidden());
    }
}
