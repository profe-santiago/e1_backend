package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.RegistroTenantRequest;
import com.clinica.mi_app.dto.response.OnboardingResponse;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final AuthService authService;
    private final OrganizacionRepository orgRepo;

    public OnboardingService(AuthService authService, OrganizacionRepository orgRepo) {
        this.authService = authService;
        this.orgRepo = orgRepo;
    }

    public OnboardingResponse registro(RegistroTenantRequest req) {
        // Paso 1: Registrar tenant en AuthService externo (crea usuario admin + tenant en InToGlobe)
        authService.registroTenant(req);

        // Paso 2: Guardar organización local
        Organizacion org = new Organizacion();
        org.setNombre(req.getNombre());
        org.setPlan(req.getPlan());
        Organizacion guardada = orgRepo.save(org);

        // Paso 3: Las API keys llegan de forma asíncrona vía evento RabbitMQ "tenant.created".
        // TenantCreatedListener se encarga de llamar configuracionTenantService.crearOActualizar(...)
        // cuando el AuthService de InToGlobe publica el evento.

        // Paso 4: Crear suscripción SaaS
        crearSuscripcion(guardada.getId(), req.getPlan());

        OnboardingResponse resp = new OnboardingResponse();
        resp.setOrganizacionId(guardada.getId());
        resp.setMensaje("Tenant registrado. Las API keys se configurarán al recibir el evento tenant-created.");
        return resp;
    }

    // TODO: conectar con PaymentClient cuando Equipo 2 lo entregue (Sprint 2)
    private void crearSuscripcion(UUID organizacionId, String plan) {
        log.debug("crearSuscripcion: no-op hasta que PaymentClient esté disponible (org={}, plan={})",
                organizacionId, plan);
    }
}
