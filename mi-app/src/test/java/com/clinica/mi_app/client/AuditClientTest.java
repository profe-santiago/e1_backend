package com.clinica.mi_app.client;

import com.clinica.mi_app.service.ConfiguracionTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AuditClientTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ConfiguracionTenantService configuracionTenantService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void audit_cuandoUrlVacia_noLlamaWebClient() {
        AuditClient client = new AuditClient(configuracionTenantService, "", webClient);

        client.audit("CREATE", "paciente", UUID.randomUUID(), UUID.randomUUID(), "Bearer token", Map.of());

        Mockito.verifyNoInteractions(webClient);
        Mockito.verifyNoInteractions(configuracionTenantService);
    }

    @Test
    public void audit_cuandoUrlEsBlank_tampocoCnsultaApiKey() {
        AuditClient client = new AuditClient(configuracionTenantService, "   ", webClient);

        client.audit("DELETE", "cita", UUID.randomUUID(), UUID.randomUUID(), null, null);

        Mockito.verifyNoInteractions(webClient);
        Mockito.verifyNoInteractions(configuracionTenantService);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void audit_cuandoUrlConfigurada_excepcionDelWebClientNoPropaga() {
        UUID orgId = UUID.randomUUID();
        when(configuracionTenantService.getAuditApiKey(orgId)).thenReturn("audit-key");
        when(webClient.post()).thenThrow(new RuntimeException("connection refused"));

        AuditClient client = new AuditClient(configuracionTenantService, "http://localhost:4003", webClient);

        assertDoesNotThrow(() ->
            client.audit("UPDATE", "cita", UUID.randomUUID(), orgId, "Bearer token", Map.of("motivo", "test"))
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void audit_cuandoUrlConfigurada_realizaLlamadaHttp() {
        UUID orgId = UUID.randomUUID();
        when(configuracionTenantService.getAuditApiKey(orgId)).thenReturn("audit-key");
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("ok"));

        AuditClient client = new AuditClient(configuracionTenantService, "http://localhost:4003", webClient);

        assertDoesNotThrow(() ->
            client.audit("CREATE", "paciente", UUID.randomUUID(), orgId, "Bearer token", Map.of())
        );

        Mockito.verify(webClient).post();
    }

    @Test
    public void auditFallback_loguea_sinLanzarExcepcion() {
        AuditClient client = new AuditClient(configuracionTenantService, "http://localhost:4003", webClient);

        assertDoesNotThrow(() -> client.auditFallback(
                "CITA_CREAR", "CITA", UUID.randomUUID(), UUID.randomUUID(), null, Map.of(),
                new RuntimeException("connection timeout")));
    }
}
