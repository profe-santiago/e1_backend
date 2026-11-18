package com.clinica.mi_app.config;

import com.clinica.mi_app.controller.AuthController;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS: se permite app.public-url (celular por Wi-Fi/túnel) y los extras; un origen desconocido se rechaza. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.public-url=http://192.168.0.197:5173/",
        "app.cors.extra-origins= https://demo.trycloudflare.com/ , *",
        "auth.public.key=dummy",
        "auth.service.url=http://localhost:4001"
})
class CorsConfigTest {

    private static final String PUBLICO = "http://192.168.0.197:5173";
    private static final String LOGIN = "{\"email\":\"nadie@ejemplo.mx\",\"password\":\"incorrecta\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        doReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())
                .when(authService).login(any(), any(), any(), any());
    }

    @Test
    void origenPublico_pasaElPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", PUBLICO)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PUBLICO))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void origenPublico_loginLlegaAlControlador_401PorCredenciales() throws Exception {
        mockMvc.perform(post("/api/auth/login").header("Origin", PUBLICO)
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", PUBLICO));
        verify(authService).login(any(), any(), any(), any());
    }

    @Test
    void localhostYOrigenExtra_siguenPermitidos() throws Exception {
        for (String origen : List.of("http://localhost:5173", "https://demo.trycloudflare.com")) {
            mockMvc.perform(options("/api/auth/login")
                            .header("Origin", origen)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origen));
        }
    }

    @Test
    void origenDesconocido_rechazado() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        mockMvc.perform(post("/api/auth/login").header("Origin", "http://192.168.0.50:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void listaDeOrigenes_limpia_sinComodinesNiDuplicados() {
        assertEquals(
                List.of("http://localhost:5173", "http://localhost:5174", PUBLICO, "https://demo.trycloudflare.com"),
                SecurityConfig.origenesPermitidos(
                        "http://localhost:5173,http://localhost:5174",
                        PUBLICO + "/",
                        " https://demo.trycloudflare.com/ , * , , http://localhost:5173"));
        assertEquals(List.of("http://localhost:5173"),
                SecurityConfig.origenesPermitidos("http://localhost:5173", null, ""));
    }
}
