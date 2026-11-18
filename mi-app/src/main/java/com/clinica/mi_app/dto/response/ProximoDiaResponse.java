package com.clinica.mi_app.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Encabezado "Mañana" de Actividad: el próximo día con citas dentro de los siguientes 7 días
 * (empezando por mañana según el reloj de la clínica). {@code fecha} es null si no hay citas.
 */
public record ProximoDiaResponse(LocalDate fecha, boolean esManana, long total, long confirmadas, long canceladas,
                                 long sinConfirmar, List<Pendiente> pendientes, List<CitaDelDia> citas) {

    public static ProximoDiaResponse vacio() {
        return new ProximoDiaResponse(null, false, 0, 0, 0, 0, List.of(), List.of());
    }

    /** Todas las citas del día, en orden de hora, para la "tira de horas" coloreada por estado. */
    public record CitaDelDia(UUID citaId, OffsetDateTime fechaHora, String estado, String pacienteNombre,
                             UUID medicoId, String medicoNombre) {}

    /** Cita SIN_CONFIRMAR candidata a recordatorio. */
    public record Pendiente(UUID citaId, OffsetDateTime fechaHora, String estado, String pacienteNombre,
                            UUID medicoId, String medicoNombre, boolean telefonoValido) {}
}
