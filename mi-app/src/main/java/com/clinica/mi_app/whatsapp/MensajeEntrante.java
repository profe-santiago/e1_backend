package com.clinica.mi_app.whatsapp;

/**
 * Mensaje del paciente ya normalizado, independiente del proveedor.
 *
 * @param id          id del proveedor (wamid de Meta / MessageSid de Twilio) para deduplicar
 * @param telefono    10 dígitos MX, o null si no se reconoció el número
 * @param texto       texto, payload de botón o id de respuesta interactiva
 * @param contextoId  id del mensaje saliente al que responde (context.id de Meta /
 *                    OriginalRepliedMessageSid de Twilio), o null
 */
public record MensajeEntrante(String id, String telefono, String texto, String contextoId) {}
