package com.clinica.mi_app.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Vista "Actividad": avisos de los últimos 30 días (máx. 200) ya filtrados, más los conteos de cada filtro. */
public record ActividadResponse(List<Item> items, Conteos conteos) {

    /**
     * Un aviso. {@code categoria}: CONFIRMACION | CANCELACION | RECORDATORIO.
     * {@code origen} (respuestas del paciente): ENLACE | WHATSAPP.
     * {@code canalEnvio} (recordatorios): "enlace" si se abrió en wa.me; si no, null.
     * {@code citaFechaHora} sigue la convención de reloj de la clínica en UTC; {@code creadaEn} es un instante real.
     */
    public record Item(UUID id, String tipo, String categoria, String estado, boolean leida, OffsetDateTime creadaEn,
                       String origen, String canalEnvio, UUID citaId, OffsetDateTime citaFechaHora, String citaEstado,
                       String pacienteNombre, UUID medicoId, String medicoNombre) {}

    public record Conteos(long todas, long sinLeer, long confirmaciones, long cancelaciones, long recordatorios) {}
}
