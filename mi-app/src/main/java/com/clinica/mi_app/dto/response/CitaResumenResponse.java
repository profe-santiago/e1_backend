package com.clinica.mi_app.dto.response;

import com.clinica.mi_app.model.Cita;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cita con nombres resueltos, para listas del dashboard. */
public record CitaResumenResponse(
        UUID id,
        OffsetDateTime fechaHora,
        String estado,
        UUID pacienteId,
        String pacienteNombre,
        String pacienteTelefono,
        UUID medicoId,
        String medicoNombre
) {
    public static CitaResumenResponse of(Cita c) {
        return new CitaResumenResponse(
                c.getId(),
                c.getFechaHora(),
                c.getEstado(),
                c.getPaciente() != null ? c.getPaciente().getId() : null,
                c.getPaciente() != null ? c.getPaciente().getNombre() : null,
                c.getPaciente() != null ? c.getPaciente().getTelefono() : null,
                c.getMedico() != null ? c.getMedico().getId() : null,
                c.getMedico() != null ? c.getMedico().getNombre() : null
        );
    }
}
