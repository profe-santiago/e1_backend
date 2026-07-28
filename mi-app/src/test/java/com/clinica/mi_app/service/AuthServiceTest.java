package com.clinica.mi_app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

public class AuthServiceTest {

    @Mock
    private WebClient authWebClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private ResponseSpec responseSpec;

    private AuthService authService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(authWebClient);
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientPost(String responseBody, HttpStatus status) {
        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class))
                .thenReturn(Mono.just(new ResponseEntity<>(responseBody, status)));
    }

    @Test
    public void testLogin_Success() {
        String expectedResponse = "{\"token\":\"jwt-token\",\"email\":\"user@example.com\"}";
        mockWebClientPost(expectedResponse, HttpStatus.OK);

        ResponseEntity<String> response = authService.login(
                "user@example.com", "password123", "eldoc", "clinicas");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    public void testRegistro_Success() {
        String expectedResponse = "{\"token\":\"jwt-token\",\"email\":\"new@example.com\"}";
        mockWebClientPost(expectedResponse, HttpStatus.CREATED);

        ResponseEntity<String> response = authService.registro(
                "new@example.com", "password123", "eldoc", "clinicas");

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    public void testLogin_ProxyReturns401() {
        String errorResponse = "{\"error\":\"Credenciales invalidas\"}";
        mockWebClientPost(errorResponse, HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> response = authService.login(
                "user@example.com", "wrongpassword", "eldoc", "clinicas");

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void testRegistro_ProxyReturns400() {
        String errorResponse = "{\"error\":\"Email ya existe\"}";
        mockWebClientPost(errorResponse, HttpStatus.BAD_REQUEST);

        ResponseEntity<String> response = authService.registro(
                "existing@example.com", "password123", "eldoc", "clinicas");

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testLogin_DefaultTenant() {
        String expectedResponse = "{\"token\":\"jwt-token\"}";
        mockWebClientPost(expectedResponse, HttpStatus.OK);

        ResponseEntity<String> response = authService.login(
                "user@example.com", "password123", "default", "clinicas");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
