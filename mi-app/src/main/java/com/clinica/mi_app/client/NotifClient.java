package com.clinica.mi_app.client;

import com.clinica.mi_app.service.ConfiguracionTenantService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Paquete client/ para clientes HTTP puros sin lógica de dominio,
// análogo a cómo AuthService/IAService viven en service/ porque orquestan negocio.
@Component
public class NotifClient {

    private static final Logger log = LoggerFactory.getLogger(NotifClient.class);

    private final WebClient notifWebClient;
    private final ConfiguracionTenantService configuracionTenantService;

    public NotifClient(WebClient notifWebClient, ConfiguracionTenantService configuracionTenantService) {
        this.notifWebClient = notifWebClient;
        this.configuracionTenantService = configuracionTenantService;
    }

    @CircuitBreaker(name = "notif-service", fallbackMethod = "fallbackSendNotification")
    public Optional<String> sendNotification(String channel, String recipient, String body, UUID organizacionId) {
        String apiKey = configuracionTenantService.getNotifApiKey(organizacionId);
        Map<String, Object> payload = Map.of("channel", channel, "recipient", recipient, "body", body);
        String response = notifWebClient.post()
                .uri("/api/notif/send")
                .header("X-Api-Key", apiKey != null ? apiKey : "")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return Optional.ofNullable(response);
    }

    public Optional<String> fallbackSendNotification(String channel, String recipient, String body,
                                                      UUID organizacionId, Exception ex) {
        log.warn("NotifClient.sendNotification falló para org={}: {}", organizacionId, ex.getMessage());
        return Optional.empty();
    }

    @CircuitBreaker(name = "notif-service", fallbackMethod = "fallbackSendTemplate")
    public Optional<String> sendTemplate(String templateName, Map<String, Object> variables,
                                          String recipient, UUID organizacionId) {
        String apiKey = configuracionTenantService.getNotifApiKey(organizacionId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("templateName", templateName);
        payload.put("variables", variables);
        payload.put("recipient", recipient);
        String response = notifWebClient.post()
                .uri("/api/notif/template")
                .header("X-Api-Key", apiKey != null ? apiKey : "")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return Optional.ofNullable(response);
    }

    public Optional<String> fallbackSendTemplate(String templateName, Map<String, Object> variables,
                                                   String recipient, UUID organizacionId, Exception ex) {
        log.warn("NotifClient.sendTemplate falló para org={}: {}", organizacionId, ex.getMessage());
        return Optional.empty();
    }
}
