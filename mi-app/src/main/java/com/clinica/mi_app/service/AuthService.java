package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.RegistroTenantRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final WebClient authWebClient;

    public AuthService(@Qualifier("authServiceWebClient") WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }

    public ResponseEntity<String> login(String email, String password, String tenantSlug, String systemId) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("tenantSlug", tenantSlug);
        body.put("systemId", systemId);

        return proxy("/auth/login", body);
    }

    public ResponseEntity<String> registro(String email, String password, String tenantSlug, String systemId) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("tenantSlug", tenantSlug);
        body.put("systemId", systemId);

        return proxy("/auth/registro", body);
    }

    // Endpoint asumido: /tenants/registro (distinto de /auth/registro que registra usuarios individuales).
    // El contrato real del AuthService de InToGlobe no estaba disponible en el roadmap al momento
    // de escribir esto — ajustar la URI cuando Equipo 2 confirme el contrato.
    public ResponseEntity<String> registroTenant(RegistroTenantRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", req.getNombre());
        body.put("plan", req.getPlan());
        body.put("email", req.getEmail());
        body.put("password", req.getPassword());
        body.put("tenantSlug", req.getTenantSlug());
        return proxy("/tenants/registro", body);
    }

    private ResponseEntity<String> proxy(String uri, Map<String, Object> body) {
        try {
            return authWebClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
        } catch (WebClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
