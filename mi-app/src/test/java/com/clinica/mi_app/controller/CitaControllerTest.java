package com.clinica.mi_app.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.mi_app.dto.request.CitaRequest;
import com.clinica.mi_app.dto.response.CitaResponse;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.CitaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(CitaController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "auth.public.key=dummy",
        "auth.service.url=http://localhost:4001"
})
public class CitaControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            return mapper;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CitaService citaService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private UUID organizacionId;
    private UUID pacienteId;
    private UUID medicoId;
    private UUID consultorioId;
    private UUID citaId;

    @BeforeEach
    public void setup() {
        organizacionId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        medicoId = UUID.randomUUID();
        consultorioId = UUID.randomUUID();
        citaId = UUID.randomUUID();
    }

    @Test
    public void testListarPorOrganizacion_Success() throws Exception {
        CitaResponse cita1 = new CitaResponse();
        cita1.setId(citaId);
        cita1.setOrganizacionId(organizacionId);
        cita1.setMedicoId(medicoId);
        cita1.setPacienteId(pacienteId);
        cita1.setFechaHora(OffsetDateTime.now().plusHours(1));
        cita1.setDuracionMin((short) 30);
        cita1.setMotivo("Consulta general");

        when(citaService.listarPorOrganizacion(organizacionId)).thenReturn(List.of(cita1));

        mockMvc.perform(get("/api/citas/organizacion/{orgId}", organizacionId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(citaId.toString())))
                .andExpect(jsonPath("$[0].medicoId", is(medicoId.toString())))
                .andExpect(jsonPath("$[0].pacienteId", is(pacienteId.toString())))
                .andExpect(jsonPath("$[0].motivo", is("Consulta general")));
    }

    @Test
    public void testListarPorMedico_Success() throws Exception {
        CitaResponse cita1 = new CitaResponse();
        cita1.setId(citaId);
        cita1.setMedicoId(medicoId);
        cita1.setFechaHora(OffsetDateTime.now().plusHours(2));
        cita1.setDuracionMin((short) 45);

        when(citaService.listarPorMedico(medicoId)).thenReturn(List.of(cita1));

        mockMvc.perform(get("/api/citas/medico/{medicoId}", medicoId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].medicoId", is(medicoId.toString())));
    }

    @Test
    public void testListarPorPaciente_Success() throws Exception {
        CitaResponse cita1 = new CitaResponse();
        cita1.setId(citaId);
        cita1.setPacienteId(pacienteId);
        cita1.setFechaHora(OffsetDateTime.now().plusHours(1));

        when(citaService.listarPorPaciente(pacienteId)).thenReturn(List.of(cita1));

        mockMvc.perform(get("/api/citas/paciente/{pacienteId}", pacienteId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pacienteId", is(pacienteId.toString())));
    }

    @Test
    public void testBuscarPorId_Success() throws Exception {
        CitaResponse cita = new CitaResponse();
        cita.setId(citaId);
        cita.setOrganizacionId(organizacionId);
        cita.setMedicoId(medicoId);
        cita.setPacienteId(pacienteId);
        cita.setConsultorioId(consultorioId);
        cita.setFechaHora(OffsetDateTime.now().plusHours(1));
        cita.setDuracionMin((short) 30);
        cita.setMotivo("Consulta general");
        cita.setEstado("CONFIRMADA");

        when(citaService.buscarPorId(citaId)).thenReturn(cita);

        mockMvc.perform(get("/api/citas/{id}", citaId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(citaId.toString())))
                .andExpect(jsonPath("$.medicoId", is(medicoId.toString())))
                .andExpect(jsonPath("$.estado", is("CONFIRMADA")));
    }

    @Test
    public void testCrearCita_Success() throws Exception {
        CitaRequest request = new CitaRequest();
        request.setOrganizacionId(organizacionId);
        request.setPacienteId(pacienteId);
        request.setMedicoId(medicoId);
        request.setConsultorioId(consultorioId);
        request.setFechaHora(OffsetDateTime.now().plusHours(1));
        request.setDuracionMin((short) 30);
        request.setMotivo("Consulta general");

        CitaResponse citaResponse = new CitaResponse();
        citaResponse.setId(citaId);
        citaResponse.setOrganizacionId(organizacionId);
        citaResponse.setMedicoId(medicoId);
        citaResponse.setPacienteId(pacienteId);
        citaResponse.setConsultorioId(consultorioId);
        citaResponse.setFechaHora(request.getFechaHora());
        citaResponse.setDuracionMin(request.getDuracionMin());
        citaResponse.setMotivo(request.getMotivo());

        when(citaService.crear(any(CitaRequest.class))).thenReturn(citaResponse);

        mockMvc.perform(post("/api/citas")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(citaId.toString())))
                .andExpect(jsonPath("$.medicoId", is(medicoId.toString())))
                .andExpect(jsonPath("$.pacienteId", is(pacienteId.toString())))
                .andExpect(jsonPath("$.motivo", is("Consulta general")));
    }

    @Test
    public void testActualizarCita_Success() throws Exception {
        CitaRequest request = new CitaRequest();
        request.setOrganizacionId(organizacionId);
        request.setPacienteId(pacienteId);
        request.setMedicoId(medicoId);
        request.setConsultorioId(consultorioId);
        request.setFechaHora(OffsetDateTime.now().plusHours(2));
        request.setDuracionMin((short) 45);
        request.setMotivo("Consulta actualizada");

        CitaResponse citaResponse = new CitaResponse();
        citaResponse.setId(citaId);
        citaResponse.setOrganizacionId(organizacionId);
        citaResponse.setPacienteId(pacienteId);
        citaResponse.setMedicoId(medicoId);
        citaResponse.setFechaHora(request.getFechaHora());
        citaResponse.setDuracionMin(request.getDuracionMin());
        citaResponse.setMotivo(request.getMotivo());

        when(citaService.actualizar(eq(citaId), any(CitaRequest.class))).thenReturn(citaResponse);

        mockMvc.perform(put("/api/citas/{id}", citaId)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(citaId.toString())))
                .andExpect(jsonPath("$.motivo", is("Consulta actualizada")));
    }

    @Test
    public void testEliminarCita_Success() throws Exception {
        mockMvc.perform(delete("/api/citas/{id}", citaId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNoContent());
    }
}
