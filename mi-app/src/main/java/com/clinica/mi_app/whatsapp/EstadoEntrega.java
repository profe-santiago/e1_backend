package com.clinica.mi_app.whatsapp;

/** Notificación de estado de un mensaje saliente (statuses[] de Meta / status callback de Twilio). */
public record EstadoEntrega(String id, String estado) {

    /** Traduce los estados de Meta y Twilio a los de whatsapp_mensaje; null si no aplica. */
    public static String traducir(String estadoProveedor) {
        if (estadoProveedor == null) return null;
        return switch (estadoProveedor.toLowerCase()) {
            case "sent", "queued", "accepted", "sending" -> "ENVIADO";
            case "delivered" -> "ENTREGADO";
            case "read" -> "LEIDO";
            case "failed", "undelivered" -> "FALLIDO";
            default -> null;
        };
    }
}
