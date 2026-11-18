package com.clinica.mi_app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resumen de cobros PAGADO en un rango de fechas (GET /api/cobros/resumen). */
public record CobrosResumenResponse(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal total,
        long cantidad,
        Map<String, BigDecimal> porMetodo,
        List<IngresoDia> porDia,
        List<IngresoMedico> porMedico
) {
    public record IngresoDia(LocalDate fecha, BigDecimal total, long cantidad) {}

    public record IngresoMedico(UUID medicoId, String nombre, BigDecimal total, long cantidad) {}
}
