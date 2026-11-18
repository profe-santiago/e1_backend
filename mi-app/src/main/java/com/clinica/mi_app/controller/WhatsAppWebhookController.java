package com.clinica.mi_app.controller;

import com.clinica.mi_app.whatsapp.TwilioWebhook;
import com.clinica.mi_app.whatsapp.WhatsAppWebhookProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * Webhooks públicos de WhatsApp. Cada endpoint solo responde con su proveedor activo
 * ({@code notif.provider}); con cualquier otro devuelve 404. Son las únicas rutas de /api/whatsapp
 * sin JWT, por eso la autenticidad se valida con la firma del proveedor.
 */
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Mensajes entrantes del paciente (Twilio / Meta)")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    static final String TWIML_VACIO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";

    private final WhatsAppWebhookProcessor processor;
    private final String provider;
    private final String twilioAuthToken;
    private final String twilioWebhookUrl;

    public WhatsAppWebhookController(WhatsAppWebhookProcessor processor,
                                     @Value("${notif.provider:simulado}") String provider,
                                     @Value("${whatsapp.twilio.auth-token:}") String twilioAuthToken,
                                     @Value("${whatsapp.twilio.webhook-url:}") String twilioWebhookUrl) {
        this.processor = processor;
        this.provider = provider;
        this.twilioAuthToken = twilioAuthToken;
        this.twilioWebhookUrl = twilioWebhookUrl;
    }

    // ── Twilio ───────────────────────────────────────────────────────────────

    @PostMapping(value = "/twilio", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Webhook de Twilio (mensaje entrante)",
            description = "Solo con notif.provider=twilio. Valida X-Twilio-Signature contra whatsapp.twilio.webhook-url y responde TwiML vacío")
    public ResponseEntity<String> twilio(@RequestParam MultiValueMap<String, String> params,
                                         @RequestHeader(value = "X-Twilio-Signature", required = false) String firma) {
        if (!"twilio".equalsIgnoreCase(provider)) return ResponseEntity.notFound().build();
        if (!TwilioWebhook.firmaValida(twilioWebhookUrl, params, twilioAuthToken, firma)) {
            log.warn("Twilio webhook: firma inválida o ausente");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            processor.procesar(TwilioWebhook.mensaje(params), null);
        } catch (Exception ex) {
            // Se responde 200 igualmente: un 5xx haría que Twilio reintente un mensaje que no se puede procesar
            log.error("Twilio webhook: error procesando mensaje: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(TWIML_VACIO);
    }

    @PostMapping(value = "/twilio/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Status callback de Twilio", description = "Actualiza el mensaje saliente a ENTREGADO / LEIDO / FALLIDO")
    public ResponseEntity<Void> twilioStatus(@RequestParam MultiValueMap<String, String> params,
                                             @RequestHeader(value = "X-Twilio-Signature", required = false) String firma) {
        if (!"twilio".equalsIgnoreCase(provider)) return ResponseEntity.notFound().build();
        String url = twilioWebhookUrl == null || twilioWebhookUrl.isBlank() ? null : twilioWebhookUrl + "/status";
        if (!TwilioWebhook.firmaValida(url, params, twilioAuthToken, firma)) {
            log.warn("Twilio status: firma inválida o ausente");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            processor.aplicarEstado(TwilioWebhook.estado(params));
        } catch (Exception ex) {
            log.error("Twilio status: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.ok().build();
    }
}
