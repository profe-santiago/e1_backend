package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.AdminDashboardResponse;
import com.clinica.mi_app.dto.response.CitaResumenResponse;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Pago;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Métricas del panel del administrador. Todas las fechas siguen la convención del sistema:
 * hora de reloj de la clínica guardada en UTC (ver {@link ClinicaReloj}).
 */
@Service
public class AdminDashboardService {

    static final List<String> ESTADOS = List.of("SIN_CONFIRMAR", "CONFIRMADA", "REAGENDADA", "NO_ASISTIO", "CANCELADA");
    static final Set<String> EXCLUIDOS_DE_TASAS = Set.of("CANCELADA", "REAGENDADA");
    static final Set<String> EXCLUIDOS_DE_PROXIMAS = Set.of("CANCELADA", "REAGENDADA");
    static final int DIAS_TASAS = 30;

    private final CitaRepository citaRepo;
    private final PagoRepository pagoRepo;
    private final CobroResumenService cobroResumenService;
    private final ClinicaReloj reloj;

    public AdminDashboardService(CitaRepository citaRepo, PagoRepository pagoRepo,
                                 CobroResumenService cobroResumenService, ClinicaReloj reloj) {
        this.citaRepo = citaRepo;
        this.pagoRepo = pagoRepo;
        this.cobroResumenService = cobroResumenService;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(UUID organizacionId) {
        LocalDate hoy = reloj.hoy();
        OffsetDateTime ahora = reloj.ahora();

        // Citas de hoy por estado
        List<Cita> citasHoy = citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                organizacionId, ClinicaReloj.inicioDelDia(hoy), ClinicaReloj.inicioDelDia(hoy.plusDays(1)));
        Map<String, Long> porEstado = new LinkedHashMap<>();
        ESTADOS.forEach(e -> porEstado.put(e, 0L));
        citasHoy.forEach(c -> porEstado.merge(c.getEstado(), 1L, Long::sum));

        // Ingresos de hoy
        BigDecimal ingresosHoy = cobroResumenService.ingresosDelDia(organizacionId, hoy);

        // Cobros pendientes
        List<Pago> pendientes = pagoRepo.findByOrganizacionIdAndEstado(organizacionId, "PENDIENTE");
        BigDecimal montoPendiente = pendientes.stream()
                .map(p -> p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tasas de los últimos 30 días (solo citas ya ocurridas)
        List<Cita> ultimos30 = citaRepo.findByOrganizacionIdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                organizacionId, ClinicaReloj.inicioDelDia(hoy.minusDays(DIAS_TASAS)), ahora);
        AdminDashboardResponse.Tasas tasas = calcularTasas(ultimos30);

        List<CitaResumenResponse> proximas = citaRepo
                .findTop8ByOrganizacionIdAndEstadoNotInAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                        organizacionId, EXCLUIDOS_DE_PROXIMAS, ahora)
                .stream().map(CitaResumenResponse::of).toList();

        List<CitaResumenResponse> sinConfirmar = citaRepo
                .findTop10ByOrganizacionIdAndEstadoAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                        organizacionId, "SIN_CONFIRMAR", ahora)
                .stream().map(CitaResumenResponse::of).toList();

        return new AdminDashboardResponse(
                hoy, citasHoy.size(), porEstado, ingresosHoy,
                pendientes.size(), montoPendiente, tasas, proximas, sinConfirmar);
    }

    static AdminDashboardResponse.Tasas calcularTasas(List<Cita> citas) {
        List<Cita> base = citas.stream().filter(c -> !EXCLUIDOS_DE_TASAS.contains(c.getEstado())).toList();
        if (base.isEmpty()) {
            return new AdminDashboardResponse.Tasas(0, 0, 0);
        }
        long confirmadas = base.stream().filter(c -> "CONFIRMADA".equals(c.getEstado())).count();
        long noAsistio = base.stream().filter(c -> "NO_ASISTIO".equals(c.getEstado())).count();
        return new AdminDashboardResponse.Tasas(
                porcentaje(confirmadas, base.size()), porcentaje(noAsistio, base.size()), base.size());
    }

    private static double porcentaje(long parte, long total) {
        return Math.round(parte * 1000.0 / total) / 10.0;
    }
}
