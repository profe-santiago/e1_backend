package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.request.PacienteRequest;
import com.clinica.mi_app.dto.response.PacienteResponse;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.PacienteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PacienteController.class)
@Import(SecurityConfig.class)
class PacienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
	private JwtFilter jwtFilter;

	@MockitoBean
	private PacienteService pacienteService;

	@MockitoBean
	private JwtUtil jwtUtil;

    private static final UUID ORG_ID = UUID.fromString("20352770-80b9-403b-9a3d-e55e02e98edd");
    private static final UUID PACIENTE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        doAnswer(invocation -> {
            var chain = invocation.getArgument(2, jakarta.servlet.FilterChain.class);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private PacienteResponse buildPacienteResponse() {
        PacienteResponse r = new PacienteResponse();
        r.setId(PACIENTE_ID);
        r.setOrganizacionId(ORG_ID);
        r.setNombre("Ana García");
        r.setEmail("ana@clinic.com");
        r.setSexo("F");
        r.setActivo(true);
        return r;
    }

    @Test
    void test_listar_pacientes_medico() throws Exception {
        when(pacienteService.listarPorOrganizacion(ORG_ID)).thenReturn(List.of(buildPacienteResponse()));

        mockMvc.perform(get("/api/pacientes/organizacion/" + ORG_ID)
                        .with(user("medico").roles("MEDICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ana García"));
    }

    @Test
    void test_crear_paciente_admin() throws Exception {
        PacienteRequest req = new PacienteRequest();
        req.setOrganizacionId(ORG_ID);
        req.setNombre("Pedro López");
        req.setEmail("pedro@clinic.com");
        req.setSexo("M");
        req.setFechaNacimiento(LocalDate.of(1990, 5, 15));

        when(pacienteService.crear(any(PacienteRequest.class))).thenReturn(buildPacienteResponse());

        mockMvc.perform(post("/api/pacientes")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void test_eliminar_paciente_paciente_denegado() throws Exception {
        mockMvc.perform(delete("/api/pacientes/" + PACIENTE_ID)
                        .with(user("paciente").roles("PACIENTE")))
                .andExpect(status().isForbidden());
    }
}
