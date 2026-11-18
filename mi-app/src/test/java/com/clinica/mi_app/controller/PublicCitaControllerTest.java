package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.dto.response.PublicCitaResponse;
import com.clinica.mi_app.exception.EnlaceException;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.security.PublicRateLimitFilter;
import com.clinica.mi_app.service.PublicCitaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoint público del canal "enlace": sin JWT SOLO en /api/public/citas/{token}, con límite por IP. */
@WebMvcTest(controllers = PublicCitaController.class)
@Import({SecurityConfig.class, PublicRateLimitFilter.class})
@TestPropertySource(properties = "public.rate-limit.por-minuto=3")
class PublicCitaControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private PublicCitaService service;

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
    private static final AtomicInteger IP = new AtomicInteger(1);

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    /** Una IP distinta por petición para que el límite no interfiera entre pruebas. */
    private static String ipNueva() {
        return "10.0.0." + IP.getAndIncrement();
    }

    private static PublicCitaResponse respuesta(String resultado) {
        return new PublicCitaResponse("Ana", "Clínica Centro", "Dra. Mariana López Ruiz", "Pediatría",
                "Consultorio 1", null, OffsetDateTime.parse("2026-11-19T10:00:00Z"), "SIN_CONFIRMAR", resultado,
                (short) 30, "9515550100");
    }

    @Test
    void get_sinAutenticacion_200() throws Exception {
        when(service.ver(TOKEN)).thenReturn(respuesta(null));

        mockMvc.perform(get("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacienteNombre").value("Ana"))
                .andExpect(jsonPath("$.medico").value("Dra. Mariana López Ruiz"))
                .andExpect(jsonPath("$.fechaHora").exists());
    }

    @Test
    void post_sinAutenticacion_200() throws Exception {
        when(service.responder(TOKEN, "CANCELAR")).thenReturn(respuesta("CANCELADA"));

        mockMvc.perform(post("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"CANCELAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("CANCELADA"));
        verify(service).responder(TOKEN, "CANCELAR");
    }

    @Test
    void soloEsaRuta_esPublica_elRestoExigeJwt() throws Exception {
        mockMvc.perform(get("/api/public/otra").header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/citas/{t}/extra", TOKEN).header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/citas").header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", java.util.UUID.randomUUID())
                        .header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void errorDelEnlace_statusCodigoYMensajeAmable() throws Exception {
        when(service.ver(TOKEN)).thenThrow(new EnlaceException(HttpStatus.GONE, "EXPIRADO", "Este enlace expiró."));

        mockMvc.perform(get("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("EXPIRADO"))
                .andExpect(jsonPath("$.mensaje").value("Este enlace expiró."));
    }

    @Test
    void accionInvalida_400_sinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"BORRAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ACCION_INVALIDA"));
        verifyNoInteractions(service);
    }

    @Test
    void limitePorIp_429_yOtraIpSigueFuncionando() throws Exception {
        when(service.ver(TOKEN)).thenReturn(respuesta(null));
        String ip = ipNueva();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ip + ", 172.18.0.1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));

        mockMvc.perform(get("/api/public/citas/{t}", TOKEN).header("X-Forwarded-For", ipNueva()))
                .andExpect(status().isOk());
        verify(service, times(4)).ver(TOKEN);
    }
}
