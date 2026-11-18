package com.clinica.mi_app.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canal de pruebas: no sale a internet. Genera un wamid simulado; el mensaje (ya renderizado)
 * queda registrado en whatsapp_mensaje por {@link WhatsAppMensajeria}, igual que con los canales reales.
 */
@Component
@ConditionalOnProperty(name = "notif.provider", havingValue = "simulado", matchIfMissing = true)
public class SimuladoWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(SimuladoWhatsAppSender.class);

    @Override
    public String proveedor() {
        return "simulado";
    }

    @Override
    public Optional<String> sendTemplate(UUID organizacionId, String telefono, String plantilla, List<String> params) {
        log.info("WhatsApp simulado: plantilla '{}' a {}", plantilla, telefono);
        return Optional.of(nuevoId());
    }

    @Override
    public Optional<String> sendText(UUID organizacionId, String telefono, String texto) {
        log.info("WhatsApp simulado: texto a {}", telefono);
        return Optional.of(nuevoId());
    }

    static String nuevoId() {
        return "wamid.SIM." + UUID.randomUUID();
    }
}
