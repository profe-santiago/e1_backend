package com.clinica.mi_app.dto.response;

import com.clinica.mi_app.model.Notificacion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificacionRecienteResponse(UUID id, String tipo, OffsetDateTime creadaEn, boolean leida, UUID citaId,
                                           OffsetDateTime citaFechaHora, String pacienteNombre, String medicoNombre,
                                           String respuesta) {

    public static NotificacionRecienteResponse of(Notificacion n) {
        var c = n.getCita();
        return new NotificacionRecienteResponse(n.getId(), n.getTipo(), n.getCreadaEn(), Boolean.TRUE.equals(n.getLeida()),
                c == null ? null : c.getId(),
                c == null ? null : c.getFechaHora(),
                c == null || c.getPaciente() == null ? null : c.getPaciente().getNombre(),
                c == null || c.getMedico() == null ? null : c.getMedico().getNombre(),
                n.getRespuesta());
    }
}
