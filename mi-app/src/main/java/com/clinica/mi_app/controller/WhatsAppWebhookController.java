package com.clinica.mi_app.controller;

import com.clinica.mi_app.service.WhatsAppRespuestaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Recibe respuestas del paciente por WhatsApp (confirmar/cancelar cita)")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppRespuestaService whatsAppRespuestaService;

    public WhatsAppWebhookController(WhatsAppRespuestaService whatsAppRespuestaService) {
        this.whatsAppRespuestaService = whatsAppRespuestaService;
    }

    // Formato de body asumido — se ajustará cuando se conecte el proveedor real (360dialog o WATI):
    // { "telefono": "+5211234567890", "mensaje": "1" }
    @PostMapping("/respuesta")
    @Operation(summary = "Recibir respuesta de WhatsApp",
            description = "Webhook publico: procesa la respuesta del paciente ('1' confirma, '2' cancela) y siempre responde 200")
    public ResponseEntity<Void> recibirRespuesta(@RequestBody Map<String, Object> body) {
        try {
            String telefono = String.valueOf(body.get("telefono"));
            String mensaje = String.valueOf(body.get("mensaje"));
            whatsAppRespuestaService.procesarRespuesta(telefono, mensaje);
        } catch (Exception ex) {
            log.error("Error procesando webhook de WhatsApp: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.ok().build();
    }
}
