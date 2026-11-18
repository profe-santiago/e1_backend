package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.client.NotifClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adaptador al NotifService de InToGlobe (se conserva el NotifClient existente). */
@Component
@ConditionalOnProperty(name = "notif.provider", havingValue = "intoglobe")
public class InToGlobeWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(InToGlobeWhatsAppSender.class);
    private static final String[] VARIABLES = {"nombrePaciente", "nombreMedico", "fecha", "hora", "consultorio"};

    private final NotifClient notifClient;

    public InToGlobeWhatsAppSender(NotifClient notifClient) {
        this.notifClient = notifClient;
    }

    @Override
    public String proveedor() {
        return "intoglobe";
    }

    @Override
    public Optional<String> sendTemplate(UUID organizacionId, String telefono, String plantilla, List<String> params) {
        try {
            Map<String, Object> vars = new HashMap<>();
            for (int i = 0; i < params.size() && i < VARIABLES.length; i++) vars.put(VARIABLES[i], params.get(i));
            return notifClient.sendTemplate(plantilla, vars, telefono, organizacionId).map(r -> nuevoId());
        } catch (RuntimeException ex) {
            log.warn("InToGlobe sendTemplate falló: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> sendText(UUID organizacionId, String telefono, String texto) {
        try {
            return notifClient.sendNotification("WHATSAPP", telefono, texto, organizacionId).map(r -> nuevoId());
        } catch (RuntimeException ex) {
            log.warn("InToGlobe sendText falló: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String nuevoId() {
        return "intoglobe." + UUID.randomUUID();
    }
}
