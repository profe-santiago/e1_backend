package com.clinica.mi_app.dto.response;

import java.time.OffsetDateTime;

/**
 * Último estado del recordatorio de una cita: ENVIADO | FALLIDO | RESPONDIO.
 * {@code canal}: "enlace" si ese recordatorio se envió por el canal enlace (wa.me); si no, null.
 */
public record RecordatorioEstadoResponse(String estado, OffsetDateTime fecha, String respuesta, String canal) {

    public RecordatorioEstadoResponse(String estado, OffsetDateTime fecha, String respuesta) {
        this(estado, fecha, respuesta, null);
    }
}
