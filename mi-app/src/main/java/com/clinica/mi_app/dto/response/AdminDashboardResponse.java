package com.clinica.mi_app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Métricas del panel del administrador (GET /api/admin/dashboard). */
public record AdminDashboardResponse(
        LocalDate fecha,
        long citasHoyTotal,
        Map<String, Long> citasHoyPorEstado,
        BigDecimal ingresosHoy,
        long cobrosPendientesCantidad,
        BigDecimal cobrosPendientesMonto,
        Tasas tasas30Dias,
        List<CitaResumenResponse> proximasCitas,
        List<CitaResumenResponse> proximasSinConfirmar
) {
    /**
     * Tasas en porcentaje (0–100, un decimal) sobre las citas ya ocurridas de los últimos 30 días,
     * excluyendo CANCELADA y REAGENDADA. base = número de citas consideradas.
     */
    public record Tasas(double confirmacion, double noAsistencia, long base) {}
}
