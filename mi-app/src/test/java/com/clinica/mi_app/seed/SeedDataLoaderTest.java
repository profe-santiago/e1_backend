package com.clinica.mi_app.seed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * El seed genera notificaciones con instantes REALES: sin fechas futuras, sin duplicados, recordatorios
 * exactamente 48/24 h antes de la cita, creada_en = enviada_en y solo unas pocas respuestas sin leer.
 */
class SeedDataLoaderTest {

    private static final ZoneId CLINICA = ZoneId.of("America/Mexico_City");
    /** Martes 17/11/2026 5:00 p.m. en la clínica (23:00Z): ya hubo consultas (y cobros) hoy. */
    private static final Instant AHORA = Instant.parse("2026-11-17T23:00:00Z");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Map<UUID, Instant> citaReal = new HashMap<>();
    private final List<Object[]> notifs = new ArrayList<>();
    /** args: [sql, org, cita, monto, metodo, concepto, estado, referencia, pagado_en] */
    private final List<Object[]> pagos = new ArrayList<>();

    @BeforeEach
    void generar() {
        new SeedDataLoader(jdbc, null, Clock.fixed(AHORA, ZoneOffset.UTC)).generar();
        for (Invocation inv : mockingDetails(jdbc).getInvocations()) {
            Object[] a = inv.getArguments();
            if (!(a.length > 0 && a[0] instanceof String sql)) continue;
            if (sql.startsWith("INSERT INTO cita ")) {
                // fecha_hora = hora de reloj de la clínica en UTC → instante real en America/Mexico_City
                LocalDateTime reloj = LocalDateTime.ofInstant(((Timestamp) a[6]).toInstant(), ZoneOffset.UTC);
                citaReal.put((UUID) a[1], reloj.atZone(CLINICA).toInstant());
            } else if (sql.startsWith("INSERT INTO notificacion ")) {
                notifs.add(a);
            } else if (sql.startsWith("INSERT INTO pago ")) {
                pagos.add(a);
            }
        }
        assertFalse(citaReal.isEmpty());
        assertFalse(notifs.isEmpty());
    }

    // args: [sql, org, cita, canal, tipo, estado, enviada_en, creada_en, leida, respuesta]
    private static UUID cita(Object[] n) { return (UUID) n[2]; }
    private static String tipo(Object[] n) { return (String) n[4]; }
    private static Instant enviada(Object[] n) { return ((Timestamp) n[6]).toInstant(); }
    private static Instant creada(Object[] n) { return ((Timestamp) n[7]).toInstant(); }
    private static boolean leida(Object[] n) { return (Boolean) n[8]; }

    @Test
    void ningunaNotificacionEnElFuturo() {
        notifs.forEach(n -> assertFalse(enviada(n).isAfter(AHORA), () -> tipo(n) + " con fecha futura: " + enviada(n)));
    }

    @Test
    void sinDuplicadosPorCitaYTipo() {
        Set<String> claves = new HashSet<>();
        notifs.forEach(n -> assertTrue(claves.add(cita(n) + "|" + tipo(n)), "duplicado " + tipo(n)));
    }

    @Test
    void recordatorios_exactamente48y24HorasAntesDelMomentoRealDeLaCita() {
        long revisados = 0;
        for (Object[] n : notifs) {
            int horas = switch (tipo(n)) {
                case "RECORDATORIO_48H" -> 48;
                case "RECORDATORIO_24H" -> 24;
                default -> 0;
            };
            if (horas == 0) continue;
            assertEquals(citaReal.get(cita(n)).minus(Duration.ofHours(horas)), enviada(n));
            revisados++;
        }
        assertTrue(revisados > 0, "el seed debe incluir recordatorios");
    }

    @Test
    void ejemploDelBug_cita1030_recordatorioALas1030HoraDeLaClinica_no0430() {
        // Para cualquier cita, la hora local (clínica) del recordatorio coincide con la hora de la cita
        notifs.stream().filter(n -> tipo(n).startsWith("RECORDATORIO_")).forEach(n -> assertEquals(
                LocalDateTime.ofInstant(citaReal.get(cita(n)), CLINICA).toLocalTime(),
                LocalDateTime.ofInstant(enviada(n), CLINICA).toLocalTime()));
    }

    @Test
    void creadaEnIgualAlEnvio() {
        notifs.forEach(n -> assertEquals(enviada(n), creada(n)));
    }

    @Test
    void soloPocasRespuestasRecientesSinLeer_yLoViejoLeido() {
        List<Object[]> sinLeer = notifs.stream().filter(n -> !leida(n)).toList();
        assertFalse(sinLeer.isEmpty(), "debe haber algunas respuestas sin leer para la campana");
        assertTrue(sinLeer.size() <= SeedDataLoader.RESPUESTAS_SIN_LEER);
        sinLeer.forEach(n -> {
            assertTrue(tipo(n).endsWith("_PACIENTE"), "solo respuestas de pacientes quedan sin leer");
            assertTrue(enviada(n).isAfter(AHORA.minus(Duration.ofDays(2))));
        });
        notifs.stream().filter(n -> enviada(n).isBefore(AHORA.minus(Duration.ofDays(2))))
                .forEach(n -> assertTrue(leida(n), "más de 2 días → leída"));
    }

    @Test
    void sinRecordatoriosParaCitasDeHoyNiFuturas() {
        Instant inicioHoy = LocalDate.ofInstant(AHORA, CLINICA).atStartOfDay(CLINICA).toInstant();
        notifs.stream()
                .filter(n -> tipo(n).startsWith("RECORDATORIO_") || tipo(n).equals("CONFIRMACION_PACIENTE"))
                .forEach(n -> assertTrue(citaReal.get(cita(n)).isBefore(inicioHoy),
                        () -> tipo(n) + " para una cita de hoy o futura: " + citaReal.get(cita(n))));
    }

    @Test
    void pagosPagadosEnLosUltimos7Dias_incluidoHoy_yNuncaDespuesDeAhora() {
        // pagado_en usa la convención de reloj de la clínica en UTC (como PagoService con reloj.ahora())
        OffsetDateTime ahoraReloj = LocalDateTime.ofInstant(AHORA, CLINICA).atOffset(ZoneOffset.UTC);
        LocalDate hoy = ahoraReloj.toLocalDate();
        List<OffsetDateTime> pagados = pagos.stream()
                .filter(p -> "PAGADO".equals(p[6]))
                .map(p -> ((Timestamp) p[8]).toInstant().atOffset(ZoneOffset.UTC))
                .toList();
        assertFalse(pagados.isEmpty());
        pagados.forEach(f -> assertFalse(f.isAfter(ahoraReloj), "pago con fecha futura: " + f));
        long ultimos7 = pagados.stream().filter(f -> !f.toLocalDate().isBefore(hoy.minusDays(6))).count();
        assertTrue(ultimos7 > 0, "debe haber ingresos en los últimos 7 días");
        assertTrue(pagados.stream().anyMatch(f -> f.toLocalDate().equals(hoy)), "debe haber ingresos de hoy");
    }

    @Test
    void notificacionesDeCita_reglas() {
        UUID org = UUID.randomUUID(), cita = UUID.randomUUID();
        Duration demora = Duration.ofMinutes(30);

        // Mañana y hoy (aunque ya haya pasado): sin recordatorios ni confirmaciones
        assertTrue(SeedDataLoader.notificacionesDeCita(org, cita, AHORA.plus(Duration.ofHours(18)), "SIN_CONFIRMAR",
                null, null, AHORA, demora).isEmpty(), "mañana");
        assertTrue(SeedDataLoader.notificacionesDeCita(org, cita, AHORA.minus(Duration.ofHours(2)), "CONFIRMADA",
                null, null, AHORA, demora).isEmpty(), "hoy, más temprano");
        assertTrue(SeedDataLoader.notificacionesDeCita(org, cita, AHORA.plus(Duration.ofDays(10)), "SIN_CONFIRMAR",
                null, null, AHORA, demora).isEmpty(), "futura");

        // Ayer, confirmada: 48 h, 24 h y la confirmación del paciente
        Instant ayer = AHORA.minus(Duration.ofHours(30));
        var confirmada = SeedDataLoader.notificacionesDeCita(org, cita, ayer, "CONFIRMADA", null, null, AHORA, demora);
        assertEquals(List.of("RECORDATORIO_48H", "RECORDATORIO_24H", "CONFIRMACION_PACIENTE"),
                confirmada.stream().map(SeedDataLoader.NotifSeed::tipo).toList());

        // Ayer, cancelada por el paciente entre el de 48 h y el de 24 h: no se recuerda una cita ya cancelada
        Instant canceladaEn = ayer.minus(Duration.ofHours(30));
        var cancelada = SeedDataLoader.notificacionesDeCita(org, cita, ayer, "CANCELADA", "PACIENTE", canceladaEn, AHORA, Duration.ZERO);
        assertEquals(List.of("RECORDATORIO_48H", "CANCELACION_PACIENTE"),
                cancelada.stream().map(SeedDataLoader.NotifSeed::tipo).toList());
        assertEquals(canceladaEn, cancelada.get(1).enviada());

        // La cancelación de una cita FUTURA sí se registra (el paciente puede cancelar antes del recordatorio)
        var futuraCancelada = SeedDataLoader.notificacionesDeCita(org, cita, AHORA.plus(Duration.ofDays(2)), "CANCELADA",
                "PACIENTE", AHORA.minus(Duration.ofHours(3)), AHORA, Duration.ZERO);
        assertEquals(List.of("CANCELACION_PACIENTE"), futuraCancelada.stream().map(SeedDataLoader.NotifSeed::tipo).toList());

        // Más de 7 días atrás: sin recordatorios
        assertTrue(SeedDataLoader.notificacionesDeCita(org, cita, AHORA.minus(Duration.ofDays(12)), "CONFIRMADA",
                null, null, AHORA, demora).isEmpty());
    }
}
