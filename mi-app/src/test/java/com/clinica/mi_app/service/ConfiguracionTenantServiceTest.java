package com.clinica.mi_app.service;

import com.clinica.mi_app.model.ConfiguracionTenant;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.ConfiguracionTenantRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class ConfiguracionTenantServiceTest {

    @Mock
    private ConfiguracionTenantRepository repo;

    @Mock
    private OrganizacionRepository orgRepo;

    private ConfiguracionTenantService service;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new ConfiguracionTenantService(repo, orgRepo);
    }

    @Test
    public void getNotifApiKey_cuandoExisteConfiguracion_devuelveKey() {
        UUID orgId = UUID.randomUUID();
        ConfiguracionTenant cfg = configuracionConKeys(orgId, "notif-key-123");
        when(repo.findByOrganizacionId(orgId)).thenReturn(Optional.of(cfg));

        String key = service.getNotifApiKey(orgId);

        assertEquals("notif-key-123", key);
    }

    @Test
    public void getStorageApiKey_cuandoExisteConfiguracion_devuelveKey() {
        UUID orgId = UUID.randomUUID();
        ConfiguracionTenant cfg = new ConfiguracionTenant();
        cfg.setOrganizacion(organizacion(orgId));
        cfg.setStorageApiKey("storage-key-456");
        when(repo.findByOrganizacionId(orgId)).thenReturn(Optional.of(cfg));

        String key = service.getStorageApiKey(orgId);

        assertEquals("storage-key-456", key);
    }

    @Test
    public void isWhatsappConectado_cuandoNoExisteConfiguracion_devuelveFalseSinExcepcion() {
        UUID orgId = UUID.randomUUID();
        when(repo.findByOrganizacionId(orgId)).thenReturn(Optional.empty());

        boolean resultado = assertDoesNotThrow(() -> service.isWhatsappConectado(orgId));

        assertFalse(resultado);
    }

    @Test
    public void isWhatsappConectado_cuandoWhatsappActivo_devuelveTrue() {
        UUID orgId = UUID.randomUUID();
        ConfiguracionTenant cfg = new ConfiguracionTenant();
        cfg.setOrganizacion(organizacion(orgId));
        cfg.setWhatsappConectado(true);
        when(repo.findByOrganizacionId(orgId)).thenReturn(Optional.of(cfg));

        assertTrue(service.isWhatsappConectado(orgId));
    }

    @Test
    public void getNotifApiKey_cuandoNoExisteConfiguracion_devuelveNull() {
        UUID orgId = UUID.randomUUID();
        when(repo.findByOrganizacionId(orgId)).thenReturn(Optional.empty());

        assertNull(service.getNotifApiKey(orgId));
    }

    // ---------- helpers ----------

    private ConfiguracionTenant configuracionConKeys(UUID orgId, String notifKey) {
        ConfiguracionTenant cfg = new ConfiguracionTenant();
        cfg.setOrganizacion(organizacion(orgId));
        cfg.setNotifApiKey(notifKey);
        return cfg;
    }

    private Organizacion organizacion(UUID id) {
        Organizacion org = new Organizacion();
        org.setId(id);
        org.setNombre("Test Org");
        org.setPlan("SOLO");
        return org;
    }
}
