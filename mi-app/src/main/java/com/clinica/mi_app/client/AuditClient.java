package com.clinica.mi_app.client;

import com.clinica.mi_app.service.ConfiguracionTenantService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    private final ConfiguracionTenantService configuracionTenantService;
    private final String auditServiceUrl;
    private final WebClient webClient;

    public AuditClient(ConfiguracionTenantService configuracionTenantService,
                       @Value("${audit.service.url:}") String auditServiceUrl) {
        this.configuracionTenantService = configuracionTenantService;
        this.auditServiceUrl = auditServiceUrl;
        // No se crea WebClient si la URL está vacía; audit() retorna inmediatamente en ese caso.
        this.webClient = auditServiceUrl.isBlank() ? null :
                WebClient.builder()
                        .baseUrl(auditServiceUrl)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
    }

    // Constructor package-private para tests con WebClient mockeado.
    AuditClient(ConfiguracionTenantService configuracionTenantService, String auditServiceUrl, WebClient webClient) {
        this.configuracionTenantService = configuracionTenantService;
        this.auditServiceUrl = auditServiceUrl;
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "audit-service", fallbackMethod = "auditFallback")
    public void audit(String action, String resource, UUID resourceId, UUID organizacionId,
                      String userToken, Map<String, Object> metadata) {
        if (auditServiceUrl.isBlank()) return;
        try {
            String apiKey = configuracionTenantService.getAuditApiKey(organizacionId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("resource", resource);
            payload.put("resourceId", resourceId != null ? resourceId.toString() : null);
            payload.put("organizacionId", organizacionId != null ? organizacionId.toString() : null);
            payload.put("metadata", metadata != null ? metadata : Map.of());
            webClient.post()
                    .uri("/api/audit/log")
                    .header("X-Api-Key", apiKey != null ? apiKey : "")
                    .header(HttpHeaders.AUTHORIZATION, userToken != null ? userToken : "")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception ex) {
            log.warn("AuditClient: falló audit action={} org={}: {}", action, organizacionId, ex.getMessage());
        }
    }

    public void auditFallback(String action, String resource, UUID resourceId, UUID organizacionId,
                               String userToken, Map<String, Object> metadata, Exception ex) {
        log.warn("AuditClient: CB abierto — audit action={} org={}: {}", action, organizacionId, ex.getMessage());
    }
}
