package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.RegistroTenantRequest;
import com.clinica.mi_app.dto.response.OnboardingResponse;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OnboardingServiceTest {

    @Mock private AuthService authService;
    @Mock private OrganizacionRepository orgRepo;

    private OnboardingService service;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new OnboardingService(authService, orgRepo);
    }

    @Test
    public void registro_exitoso_guardaOrganizacionYDevuelveOrganizacionId() {
        RegistroTenantRequest req = nuevaRequest();
        Organizacion orgGuardada = new Organizacion();
        orgGuardada.setId(UUID.randomUUID());
        orgGuardada.setNombre("Clínica Test");
        orgGuardada.setPlan("CLINICA");

        when(authService.registroTenant(any())).thenReturn(ResponseEntity.ok("{}"));
        when(orgRepo.save(any())).thenReturn(orgGuardada);

        OnboardingResponse result = assertDoesNotThrow(() -> service.registro(req));

        assertNotNull(result);
        assertEquals(orgGuardada.getId(), result.getOrganizacionId());
        assertNotNull(result.getMensaje());
        verify(orgRepo).save(any(Organizacion.class));
    }

    @Test
    public void registro_pasoSuscripcionNoOp_noLanzaExcepcion() {
        RegistroTenantRequest req = nuevaRequest();
        Organizacion orgGuardada = new Organizacion();
        orgGuardada.setId(UUID.randomUUID());

        when(authService.registroTenant(any())).thenReturn(ResponseEntity.ok("{}"));
        when(orgRepo.save(any())).thenReturn(orgGuardada);

        // crearSuscripcion() es no-op; el registro no debe fallar por eso
        assertDoesNotThrow(() -> service.registro(req));
    }

    @Test
    public void registro_llamaAuthServiceConDatosDelRequest() {
        RegistroTenantRequest req = nuevaRequest();
        Organizacion orgGuardada = new Organizacion();
        orgGuardada.setId(UUID.randomUUID());

        when(authService.registroTenant(any())).thenReturn(ResponseEntity.ok("{}"));
        when(orgRepo.save(any())).thenReturn(orgGuardada);

        service.registro(req);

        verify(authService).registroTenant(req);
    }

    // ---------- helpers ----------

    private RegistroTenantRequest nuevaRequest() {
        RegistroTenantRequest req = new RegistroTenantRequest();
        req.setNombre("Clínica Test");
        req.setPlan("CLINICA");
        req.setEmail("admin@clinicatest.com");
        req.setPassword("pass1234");
        req.setTenantSlug("clinicatest");
        return req;
    }
}
