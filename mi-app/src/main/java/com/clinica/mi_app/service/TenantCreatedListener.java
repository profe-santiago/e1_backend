package com.clinica.mi_app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class TenantCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(TenantCreatedListener.class);

    private final ConfiguracionTenantService configuracionTenantService;

    public TenantCreatedListener(ConfiguracionTenantService configuracionTenantService) {
        this.configuracionTenantService = configuracionTenantService;
    }

    // Cola: el roadmap nombra el evento como "tenant.created"; la cola se llama "clinicas-tenant-created"
    // siguiendo la convención {systemId}-{evento} usada en InToGlobe. Configurable vía
    // onboarding.tenant-created.queue para no hardcodear el nombre en producción.
    @RabbitListener(queues = "${onboarding.tenant-created.queue:clinicas-tenant-created}")
    public void onTenantCreated(Map<String, Object> body) {
        try {
            String orgIdStr = (String) body.get("organizacionId");
            if (orgIdStr == null || orgIdStr.isBlank()) {
                log.error("TenantCreatedListener: evento sin organizacionId, ignorando. body={}", body);
                return;
            }
            UUID organizacionId = UUID.fromString(orgIdStr);
            String notifApiKey   = (String) body.get("notifApiKey");
            String storageApiKey = (String) body.get("storageApiKey");
            String paymentApiKey = (String) body.get("paymentApiKey");
            String auditApiKey   = (String) body.get("auditApiKey");

            configuracionTenantService.crearOActualizar(
                    organizacionId, notifApiKey, storageApiKey, paymentApiKey, auditApiKey, false, null);

            log.info("TenantCreatedListener: configuración guardada para org={}", organizacionId);
        } catch (Exception ex) {
            // Un mensaje malformado nunca debe tumbar el listener ni el canal de RabbitMQ.
            log.error("TenantCreatedListener: error procesando evento tenant-created: {}", ex.getMessage(), ex);
        }
    }
}
