package com.clinica.mi_app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Reloj de la clínica.
 *
 * Convención del sistema: las horas de consulta se guardan como "hora de reloj" de la
 * clínica expresada en UTC (una cita a las 10:00 en la clínica se guarda 10:00Z).
 * Por eso cualquier comparación contra "ahora" debe usar {@link #ahora()} y no
 * OffsetDateTime.now(UTC), que adelantaría 6 h el reloj respecto a la agenda.
 */
@Component
public class ClinicaReloj {

    public static final ZoneId ZONA_CLINICA = ZoneId.of("America/Mexico_City");

    private final Clock clock;

    @Autowired
    public ClinicaReloj() {
        this(Clock.systemUTC());
    }

    public ClinicaReloj(Clock clock) {
        this.clock = clock;
    }

    /** Hora de reloj actual de la clínica, expresada en UTC (misma convención que cita.fechaHora). */
    public OffsetDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ZONA_CLINICA)).atOffset(ZoneOffset.UTC);
    }

    /** Fecha actual en la clínica. */
    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA_CLINICA));
    }

    /** Inicio (00:00Z) del día indicado según la convención de reloj en UTC. */
    public static OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
