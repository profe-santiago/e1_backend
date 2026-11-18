package com.clinica.mi_app.client;

import com.clinica.mi_app.service.ConfiguracionTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class NotifClientTest {

    @Mock private WebClient notifWebClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ConfiguracionTenantService configuracionTenantService;

    private NotifClient notifClient;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        notifClient = new NotifClient(notifWebClient, configuracionTenantService);
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientPost(String responseBody) {
        when(notifWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    @Test
    public void sendNotification_cuandoExitoso_devuelveOptionalConBody() {
        UUID orgId = UUID.randomUUID();
        when(configuracionTenantService.getNotifApiKey(orgId)).thenReturn("test-key");
        mockWebClientPost("{\"status\":\"ok\"}");

        Optional<String> result = notifClient.sendNotification("email", "user@test.com", "Hola", orgId);

        assertTrue(result.isPresent());
        assertEquals("{\"status\":\"ok\"}", result.get());
    }

    @Test
    public void sendNotification_cuandoApiKeyNula_llamamosConHeaderVacio() {
        UUID orgId = UUID.randomUUID();
        when(configuracionTenantService.getNotifApiKey(orgId)).thenReturn(null);
        mockWebClientPost("{\"status\":\"ok\"}");

        Optional<String> result = notifClient.sendNotification("sms", "5512345678", "Cita mañana", orgId);

        assertTrue(result.isPresent());
    }

    @Test
    public void fallbackSendNotification_devuelveOptionalEmpty_sinExcepcion() {
        UUID orgId = UUID.randomUUID();

        Optional<String> result = assertDoesNotThrow(() ->
            notifClient.fallbackSendNotification("email", "user@test.com", "Hola", orgId,
                    new RuntimeException("timeout"))
        );

        assertFalse(result.isPresent());
    }

    @Test
    public void sendTemplate_cuandoExitoso_devuelveOptionalConBody() {
        UUID orgId = UUID.randomUUID();
        when(configuracionTenantService.getNotifApiKey(orgId)).thenReturn("test-key");
        mockWebClientPost("{\"status\":\"enviado\"}");

        Optional<String> result = notifClient.sendTemplate(
                "bienvenida", Map.of("nombre", "Juan"), "juan@test.com", orgId);

        assertTrue(result.isPresent());
        assertEquals("{\"status\":\"enviado\"}", result.get());
    }

    @Test
    public void fallbackSendTemplate_devuelveOptionalEmpty_sinExcepcion() {
        UUID orgId = UUID.randomUUID();

        Optional<String> result = assertDoesNotThrow(() ->
            notifClient.fallbackSendTemplate("bienvenida", Map.of(), "user@test.com", orgId,
                    new RuntimeException("circuit breaker open"))
        );

        assertFalse(result.isPresent());
    }
}
