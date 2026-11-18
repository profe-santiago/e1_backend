package com.clinica.mi_app.whatsapp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canal de salida de WhatsApp intercambiable por {@code notif.provider}
 * (simulado | twilio | meta | intoglobe).
 *
 * <p>Contrato: los teléfonos llegan en 10 dígitos MX; cada implementación los adapta a su formato.
 * Devuelve el id del mensaje del proveedor (wamid de Meta, SID de Twilio...) o vacío si falló.
 * <b>Nunca</b> propaga excepciones al llamador.
 *
 * <p>{@code organizacionId} solo lo usa el adaptador de InToGlobe (API key por tenant).
 */
public interface WhatsAppSender {

    String proveedor();

    Optional<String> sendTemplate(UUID organizacionId, String telefono, String plantilla, List<String> params);

    Optional<String> sendText(UUID organizacionId, String telefono, String texto);
}
