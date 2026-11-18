package com.clinica.mi_app.dto.response;

import com.clinica.mi_app.whatsapp.EnlaceWhatsAppSender;

import com.clinica.mi_app.model.WhatsAppMensaje;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code canal}: "enlace" si el saliente se preparó para abrir en wa.me (panel: "Enlace enviado"); si no, null. */
public record WhatsAppMensajeResponse(UUID id, UUID citaId, String telefono, String direccion, String texto,
                                      String estado, OffsetDateTime createdAt, String canal) {

    public static WhatsAppMensajeResponse of(WhatsAppMensaje m) {
        String canal = m.getWamid() != null && m.getWamid().startsWith(EnlaceWhatsAppSender.PREFIJO_ID)
                ? EnlaceWhatsAppSender.PROVEEDOR : null;
        return new WhatsAppMensajeResponse(m.getId(), m.getCita() == null ? null : m.getCita().getId(),
                m.getTelefono(), m.getDireccion(), m.getTexto(), m.getEstado(), m.getCreatedAt(), canal);
    }
}
