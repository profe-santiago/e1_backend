package com.clinica.mi_app.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canal "enlace": el sistema no envía nada por su cuenta. El recordatorio se abre en WhatsApp
 * (https://wa.me/52...) desde el navegador de quien da clic, y el paciente responde en la página
 * pública /c/{token}. El id generado solo sirve para registrar el mensaje como ENVIADO.
 */
@Component
@ConditionalOnProperty(name = "notif.provider", havingValue = "enlace")
public class EnlaceWhatsAppSender implements WhatsAppSender {

    public static final String PROVEEDOR = "enlace";
    public static final String PREFIJO_ID = "enlace-";

    private static final Logger log = LoggerFactory.getLogger(EnlaceWhatsAppSender.class);

    @Override
    public String proveedor() {
        return PROVEEDOR;
    }

    @Override
    public Optional<String> sendTemplate(UUID organizacionId, String telefono, String plantilla, List<String> params) {
        log.info("WhatsApp enlace: plantilla '{}' preparada para abrir en wa.me", plantilla);
        return Optional.of(nuevoId());
    }

    @Override
    public Optional<String> sendText(UUID organizacionId, String telefono, String texto) {
        log.info("WhatsApp enlace: texto preparado para abrir en wa.me");
        return Optional.of(nuevoId());
    }

    static String nuevoId() {
        return PREFIJO_ID + UUID.randomUUID();
    }
}
