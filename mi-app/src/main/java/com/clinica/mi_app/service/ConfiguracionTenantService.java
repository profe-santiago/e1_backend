package com.clinica.mi_app.service;

import com.clinica.mi_app.model.ConfiguracionTenant;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.repository.ConfiguracionTenantRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfiguracionTenantService {

    // Cache en memoria: evita hits a BD repetidos. TTL de 5 minutos por entrada.
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final ConfiguracionTenantRepository repo;
    private final OrganizacionRepository orgRepo;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public ConfiguracionTenantService(ConfiguracionTenantRepository repo,
                                      OrganizacionRepository orgRepo) {
        this.repo = repo;
        this.orgRepo = orgRepo;
    }

    public String getNotifApiKey(UUID organizacionId) {
        return buscar(organizacionId).map(ConfiguracionTenant::getNotifApiKey).orElse(null);
    }

    public String getStorageApiKey(UUID organizacionId) {
        return buscar(organizacionId).map(ConfiguracionTenant::getStorageApiKey).orElse(null);
    }

    public String getPaymentApiKey(UUID organizacionId) {
        return buscar(organizacionId).map(ConfiguracionTenant::getPaymentApiKey).orElse(null);
    }

    public String getAuditApiKey(UUID organizacionId) {
        return buscar(organizacionId).map(ConfiguracionTenant::getAuditApiKey).orElse(null);
    }

    // Devuelve false si la organización aún no tiene fila en configuracion_tenant (onboarding pendiente).
    public boolean isWhatsappConectado(UUID organizacionId) {
        return buscar(organizacionId)
                .map(c -> Boolean.TRUE.equals(c.getWhatsappConectado()))
                .orElse(false);
    }

    // Listo para Sprint 2 parte 2 (saga de onboarding).
    public ConfiguracionTenant crearOActualizar(UUID organizacionId,
                                                String notifApiKey,
                                                String storageApiKey,
                                                String paymentApiKey,
                                                String auditApiKey,
                                                Boolean whatsappConectado,
                                                String whatsappNumero) {
        Organizacion org = orgRepo.findById(organizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", organizacionId.toString()));

        ConfiguracionTenant cfg = repo.findByOrganizacionId(organizacionId)
                .orElseGet(() -> {
                    ConfiguracionTenant nuevo = new ConfiguracionTenant();
                    nuevo.setOrganizacion(org);
                    return nuevo;
                });

        cfg.setNotifApiKey(notifApiKey);
        cfg.setStorageApiKey(storageApiKey);
        cfg.setPaymentApiKey(paymentApiKey);
        cfg.setAuditApiKey(auditApiKey);
        cfg.setWhatsappConectado(whatsappConectado != null ? whatsappConectado : false);
        cfg.setWhatsappNumero(whatsappNumero);

        ConfiguracionTenant guardado = repo.save(cfg);
        cache.put(organizacionId, new CacheEntry(guardado));
        return guardado;
    }

    private Optional<ConfiguracionTenant> buscar(UUID organizacionId) {
        CacheEntry entrada = cache.get(organizacionId);
        if (entrada != null && !entrada.estaExpirada()) {
            return Optional.ofNullable(entrada.valor);
        }
        Optional<ConfiguracionTenant> resultado = repo.findByOrganizacionId(organizacionId);
        cache.put(organizacionId, new CacheEntry(resultado.orElse(null)));
        return resultado;
    }

    private static class CacheEntry {
        final ConfiguracionTenant valor;
        final long creadoEn;

        CacheEntry(ConfiguracionTenant valor) {
            this.valor = valor;
            this.creadoEn = Instant.now().toEpochMilli();
        }

        boolean estaExpirada() {
            return Instant.now().toEpochMilli() - creadoEn > CACHE_TTL_MS;
        }
    }
}
