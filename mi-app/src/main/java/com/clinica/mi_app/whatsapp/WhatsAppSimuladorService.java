package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canal de pruebas: el usuario escribe "como paciente". Se construye un payload <b>idéntico</b> al
 * webhook de WhatsApp Cloud API y se pasa por el mismo parser y el mismo flujo que el webhook real
 * (no es un atajo al servicio de respuestas).
 */
@Service
public class WhatsAppSimuladorService {

    static final String PHONE_NUMBER_ID_SIMULADO = "SIMULADO";

    private final MetaWebhookParser parser;
    private final WhatsAppWebhookProcessor processor;
    private final WhatsAppConsultaService consulta;
    private final ObjectMapper mapper = new ObjectMapper();

    public WhatsAppSimuladorService(MetaWebhookParser parser, WhatsAppWebhookProcessor processor,
                                    WhatsAppConsultaService consulta) {
        this.parser = parser;
        this.processor = processor;
        this.consulta = consulta;
    }

    public void responder(UUID organizacionId, String telefono, String texto) {
        String tel = TelefonoMx.aDiezDigitos(telefono);
        if (tel == null) throw new IllegalArgumentException("Teléfono inválido: se esperan 10 dígitos");
        // La conversación debe existir en la organización del usuario: así el simulador no puede
        // contestar recordatorios de otra clínica aunque el paciente comparta teléfono.
        WhatsAppMensaje ultimo = consulta.ultimoSaliente(organizacionId, tel)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación de WhatsApp", tel));

        String payload = payloadMeta(tel, texto, ultimo.getWamid());
        for (MensajeEntrante m : parser.mensajes(payload)) {
            processor.procesar(m, organizacionId);
        }
    }

    /** Mismo formato que envía Meta para un mensaje de texto (con context al mensaje citado). */
    String payloadMeta(String telefono10, String texto, String contextoId) {
        String waId = "52" + telefono10;
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("from", waId);
        message.put("id", "wamid.SIM.IN." + UUID.randomUUID());
        message.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        message.put("type", "text");
        message.put("text", Map.of("body", texto));
        if (contextoId != null) {
            message.put("context", Map.of("from", "15550000000", "id", contextoId));
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("messaging_product", "whatsapp");
        value.put("metadata", Map.of("display_phone_number", "15550000000", "phone_number_id", PHONE_NUMBER_ID_SIMULADO));
        value.put("contacts", List.of(Map.of("profile", Map.of("name", "Paciente"), "wa_id", waId)));
        value.put("messages", List.of(message));
        Map<String, Object> root = Map.of(
                "object", "whatsapp_business_account",
                "entry", List.of(Map.of(
                        "id", PHONE_NUMBER_ID_SIMULADO,
                        "changes", List.of(Map.of("field", "messages", "value", value)))));
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
