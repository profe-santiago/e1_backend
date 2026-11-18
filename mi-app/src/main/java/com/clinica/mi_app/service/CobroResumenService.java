package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.response.CobrosResumenResponse;
import com.clinica.mi_app.dto.response.CobrosResumenResponse.IngresoDia;
import com.clinica.mi_app.dto.response.CobrosResumenResponse.IngresoMedico;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Pago;
import com.clinica.mi_app.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de ingresos (pagos PAGADO) por rango de fechas.
 * La fecha de cada pago es COALESCE(pagadoEn, cita.fechaHora), en la convención de
 * hora de reloj de la clínica guardada en UTC.
 */
@Service
public class CobroResumenService {

    public static final List<String> METODOS = List.of("EFECTIVO", "TRANSFERENCIA", "TARJETA");
    static final int DIAS_POR_DEFECTO = 30;
    static final int MAX_DIAS = 366;

    private final PagoRepository pagoRepo;
    private final ClinicaReloj reloj;

    public CobroResumenService(PagoRepository pagoRepo, ClinicaReloj reloj) {
        this.pagoRepo = pagoRepo;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public CobrosResumenResponse resumen(UUID organizacionId, LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : reloj.hoy();
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > MAX_DIAS) {
            throw new IllegalArgumentException("El rango máximo es de " + MAX_DIAS + " días");
        }

        List<Pago> pagos = pagoRepo.findPagadosEnRango(organizacionId,
                ClinicaReloj.inicioDelDia(inicio), ClinicaReloj.inicioDelDia(fin.plusDays(1)));

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        METODOS.forEach(m -> porMetodo.put(m, BigDecimal.ZERO));

        Map<LocalDate, BigDecimal> totalDia = new LinkedHashMap<>();
        Map<LocalDate, Long> cantidadDia = new LinkedHashMap<>();
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            totalDia.put(d, BigDecimal.ZERO);
            cantidadDia.put(d, 0L);
        }

        Map<UUID, MedicoAcumulado> porMedico = new LinkedHashMap<>();

        for (Pago p : pagos) {
            BigDecimal monto = p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO;
            total = total.add(monto);
            porMetodo.merge(p.getMetodo() != null ? p.getMetodo() : "OTRO", monto, BigDecimal::add);

            LocalDate dia = fechaEfectiva(p).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            totalDia.computeIfPresent(dia, (k, v) -> v.add(monto));
            cantidadDia.computeIfPresent(dia, (k, v) -> v + 1);

            Medico medico = p.getCita() != null ? p.getCita().getMedico() : null;
            if (medico != null) {
                porMedico.computeIfAbsent(medico.getId(), id -> new MedicoAcumulado(id, medico.getNombre()))
                        .sumar(monto);
            }
        }

        List<IngresoDia> dias = new ArrayList<>();
        totalDia.forEach((d, t) -> dias.add(new IngresoDia(d, t, cantidadDia.get(d))));

        List<IngresoMedico> medicos = porMedico.values().stream()
                .map(a -> new IngresoMedico(a.id, a.nombre, a.total, a.cantidad))
                .sorted(Comparator.comparing(IngresoMedico::total).reversed())
                .toList();

        return new CobrosResumenResponse(inicio, fin, total, pagos.size(), porMetodo, dias, medicos);
    }

    /** Suma de ingresos PAGADO en un día de la clínica. */
    @Transactional(readOnly = true)
    public BigDecimal ingresosDelDia(UUID organizacionId, LocalDate dia) {
        return pagoRepo.findPagadosEnRango(organizacionId,
                        ClinicaReloj.inicioDelDia(dia), ClinicaReloj.inicioDelDia(dia.plusDays(1)))
                .stream()
                .map(p -> p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static OffsetDateTime fechaEfectiva(Pago p) {
        return p.getPagadoEn() != null ? p.getPagadoEn() : p.getCita().getFechaHora();
    }

    private static final class MedicoAcumulado {
        private final UUID id;
        private final String nombre;
        private BigDecimal total = BigDecimal.ZERO;
        private long cantidad;

        private MedicoAcumulado(UUID id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        private void sumar(BigDecimal monto) {
            total = total.add(monto);
            cantidad++;
        }
    }
}
