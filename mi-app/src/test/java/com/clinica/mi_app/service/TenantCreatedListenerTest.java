package com.clinica.mi_app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TenantCreatedListenerTest {

    @Mock private ConfiguracionTenantService configuracionTenantService;

    private TenantCreatedListener listener;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        listener = new TenantCreatedListener(configuracionTenantService);
    }

    @Test
    public void onTenantCreated_bodyValido_llamaCrearOActualizar() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("organizacionId", orgId.toString());
        body.put("notifApiKey", "nk-123");
        body.put("storageApiKey", "sk-456");
        body.put("paymentApiKey", "pk-789");
        body.put("auditApiKey", "ak-012");

        listener.onTenantCreated(body);

        verify(configuracionTenantService).crearOActualizar(
                eq(orgId), eq("nk-123"), eq("sk-456"), eq("pk-789"), eq("ak-012"),
                eq(false), isNull());
    }

    @Test
    public void onTenantCreated_bodySinOrganizacionId_noLanzaExcepcionNiLlamaServicio() {
        Map<String, Object> body = Map.of("notifApiKey", "nk-123");

        assertDoesNotThrow(() -> listener.onTenantCreated(body));
        verifyNoInteractions(configuracionTenantService);
    }

    @Test
    public void onTenantCreated_organizacionIdFormatoInvalido_noLanzaExcepcion() {
        Map<String, Object> body = new HashMap<>();
        body.put("organizacionId", "no-es-un-uuid");

        assertDoesNotThrow(() -> listener.onTenantCreated(body));
        verifyNoInteractions(configuracionTenantService);
    }

    @Test
    public void onTenantCreated_servicioLanzaExcepcion_noSePropagaAlListener() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("organizacionId", orgId.toString());

        doThrow(new RuntimeException("BD no disponible"))
                .when(configuracionTenantService).crearOActualizar(any(), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onTenantCreated(body));
    }
}
