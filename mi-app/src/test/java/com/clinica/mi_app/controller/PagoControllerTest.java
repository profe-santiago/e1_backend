package com.clinica.mi_app.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

import com.clinica.mi_app.dto.request.PagoRequest;
import com.clinica.mi_app.dto.response.PagoResponse;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.PagoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(PagoController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "auth.public.key=dummy",
        "auth.service.url=http://localhost:4001"
})
public class PagoControllerTest {

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
    private PagoService pagoService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private UUID organizacionId;
    private UUID citaId;
    private UUID pagoId;

    @BeforeEach
    public void setup() {
        organizacionId = UUID.randomUUID();
        citaId = UUID.randomUUID();
        pagoId = UUID.randomUUID();
    }

    @Test
    public void testListarPorOrganizacion_Success() throws Exception {
        PagoResponse pago1 = new PagoResponse();
        pago1.setId(pagoId);
        pago1.setOrganizacionId(organizacionId);
        pago1.setCitaId(citaId);
        pago1.setMonto(new BigDecimal("100.50"));
        pago1.setMetodo("TARJETA");
        pago1.setEstado("PAGADO");
        pago1.setPagadoEn(OffsetDateTime.now());

        when(pagoService.listarPorOrganizacion(organizacionId)).thenReturn(List.of(pago1));

        mockMvc.perform(get("/api/pagos/organizacion/{orgId}", organizacionId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(pagoId.toString())))
                .andExpect(jsonPath("$[0].monto", is(100.50)))
                .andExpect(jsonPath("$[0].metodo", is("TARJETA")))
                .andExpect(jsonPath("$[0].estado", is("PAGADO")));
    }

    @Test
    public void testListarPorEstado_Success() throws Exception {
        PagoResponse pago1 = new PagoResponse();
        pago1.setId(pagoId);
        pago1.setOrganizacionId(organizacionId);
        pago1.setEstado("PENDIENTE");
        pago1.setMonto(new BigDecimal("50.00"));

        when(pagoService.listarPorEstado(organizacionId, "PENDIENTE")).thenReturn(List.of(pago1));

        mockMvc.perform(get("/api/pagos/organizacion/{orgId}/estado/{estado}", organizacionId, "PENDIENTE")
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado", is("PENDIENTE")));
    }

    @Test
    public void testBuscarPorCita_Success() throws Exception {
        PagoResponse pagoResponse = new PagoResponse();
        pagoResponse.setId(pagoId);
        pagoResponse.setCitaId(citaId);
        pagoResponse.setOrganizacionId(organizacionId);
        pagoResponse.setMonto(new BigDecimal("100.00"));
        pagoResponse.setMetodo("EFECTIVO");
        pagoResponse.setEstado("PAGADO");

        when(pagoService.buscarPorCita(citaId)).thenReturn(Optional.of(pagoResponse));

        mockMvc.perform(get("/api/pagos/cita/{citaId}", citaId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(pagoId.toString())))
                .andExpect(jsonPath("$.citaId", is(citaId.toString())))
                .andExpect(jsonPath("$.monto", is(100.00)));
    }

    @Test
    public void testBuscarPorId_Success() throws Exception {
        PagoResponse pago = new PagoResponse();
        pago.setId(pagoId);
        pago.setOrganizacionId(organizacionId);
        pago.setCitaId(citaId);
        pago.setMonto(new BigDecimal("150.75"));
        pago.setMetodo("TRANSFERENCIA");
        pago.setConcepto("Consulta medica");
        pago.setEstado("PAGADO");
        pago.setReferencia("REF-001");

        when(pagoService.buscarPorId(pagoId)).thenReturn(pago);

        mockMvc.perform(get("/api/pagos/{id}", pagoId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(pagoId.toString())))
                .andExpect(jsonPath("$.monto", is(150.75)))
                .andExpect(jsonPath("$.metodo", is("TRANSFERENCIA")))
                .andExpect(jsonPath("$.estado", is("PAGADO")));
    }

    @Test
    public void testCrearPago_Success() throws Exception {
        PagoRequest request = new PagoRequest();
        request.setOrganizacionId(organizacionId);
        request.setCitaId(citaId);
        request.setMonto(new BigDecimal("120.00"));
        request.setMetodo("TARJETA");
        request.setConcepto("Servicios medicos");
        request.setReferencia("REF-002");

        PagoResponse pagoResponse = new PagoResponse();
        pagoResponse.setId(pagoId);
        pagoResponse.setOrganizacionId(organizacionId);
        pagoResponse.setCitaId(citaId);
        pagoResponse.setMonto(request.getMonto());
        pagoResponse.setMetodo(request.getMetodo());
        pagoResponse.setConcepto(request.getConcepto());
        pagoResponse.setReferencia(request.getReferencia());
        pagoResponse.setEstado("PAGADO");

        when(pagoService.crear(any(PagoRequest.class))).thenReturn(pagoResponse);

        mockMvc.perform(post("/api/pagos")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(pagoId.toString())))
                .andExpect(jsonPath("$.monto", is(120.00)))
                .andExpect(jsonPath("$.metodo", is("TARJETA")));
    }

    @Test
    public void testActualizarPago_Success() throws Exception {
        PagoRequest request = new PagoRequest();
        request.setOrganizacionId(organizacionId);
        request.setCitaId(citaId);
        request.setMonto(new BigDecimal("130.00"));
        request.setMetodo("TRANSFERENCIA");

        PagoResponse pagoResponse = new PagoResponse();
        pagoResponse.setId(pagoId);
        pagoResponse.setOrganizacionId(organizacionId);
        pagoResponse.setCitaId(citaId);
        pagoResponse.setMonto(request.getMonto());
        pagoResponse.setMetodo(request.getMetodo());

        when(pagoService.actualizar(eq(pagoId), any(PagoRequest.class))).thenReturn(pagoResponse);

        mockMvc.perform(put("/api/pagos/{id}", pagoId)
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(pagoId.toString())))
                .andExpect(jsonPath("$.monto", is(130.00)))
                .andExpect(jsonPath("$.metodo", is("TRANSFERENCIA")));
    }

    @Test
    public void testEliminarPago_Success() throws Exception {
        mockMvc.perform(delete("/api/pagos/{id}", pagoId)
                .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNoContent());
    }
}
