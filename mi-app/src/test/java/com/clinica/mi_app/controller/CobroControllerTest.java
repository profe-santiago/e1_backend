package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.CobrosResumenResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.CobroResumenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.clinica.mi_app.controller.AdminDashboardControllerTest.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CobroController.class)
@Import(SecurityConfig.class)
class CobroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtFilter jwtFilter;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CobroResumenService service;

    private static final UUID ORG_ID = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            var chain = invocation.getArgument(2, jakarta.servlet.FilterChain.class);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private static CobrosResumenResponse resumen(LocalDate desde, LocalDate hasta) {
        Map<String, BigDecimal> metodos = new LinkedHashMap<>();
        metodos.put("EFECTIVO", new BigDecimal("650.00"));
        metodos.put("TRANSFERENCIA", new BigDecimal("500.00"));
        metodos.put("TARJETA", BigDecimal.ZERO);
        return new CobrosResumenResponse(desde, hasta, new BigDecimal("1150.00"), 2, metodos,
                List.of(new CobrosResumenResponse.IngresoDia(hasta, new BigDecimal("1150.00"), 2)),
                List.of(new CobrosResumenResponse.IngresoMedico(UUID.randomUUID(), "Dra. Mariana López",
                        new BigDecimal("650.00"), 1)));
    }

    @Test
    void resumen_admin_conRango() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 4);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        when(service.resumen(ORG_ID, desde, hasta)).thenReturn(resumen(desde, hasta));

        mockMvc.perform(get("/api/cobros/resumen")
                        .param("desde", "2026-11-04")
                        .param("hasta", "2026-11-10")
                        .with(como("ADMIN", ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1150.00))
                .andExpect(jsonPath("$.porMetodo.EFECTIVO").value(650.00))
                .andExpect(jsonPath("$.porDia[0].fecha").value("2026-11-10"))
                .andExpect(jsonPath("$.porMedico[0].nombre").value("Dra. Mariana López"));
    }

    @Test
    void resumen_admin_sinParametros_pasaNulls() throws Exception {
        when(service.resumen(eq(ORG_ID), isNull(), isNull()))
                .thenReturn(resumen(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 10)));

        mockMvc.perform(get("/api/cobros/resumen").with(como("ADMIN", ORG_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-12"));
    }

    @Test
    void resumen_rangoInvalido_400() throws Exception {
        when(service.resumen(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'"));

        mockMvc.perform(get("/api/cobros/resumen")
                        .param("desde", "2026-11-10")
                        .param("hasta", "2026-11-01")
                        .with(como("ADMIN", ORG_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumen_medico_403() throws Exception {
        mockMvc.perform(get("/api/cobros/resumen").with(como("MEDICO", ORG_ID)))
                .andExpect(status().isForbidden());
        verify(service, never()).resumen(any(), any(), any());
    }
}
