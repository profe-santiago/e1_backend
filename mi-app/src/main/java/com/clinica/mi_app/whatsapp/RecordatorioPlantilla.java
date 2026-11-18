package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.model.Cita;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Textos de WhatsApp en español. La plantilla de recordatorio tiene 5 parámetros en este orden:
 * {{1}} paciente, {{2}} médico, {{3}} fecha, {{4}} hora, {{5}} consultorio — el mismo cuerpo que
 * se registra como plantilla en Meta.
 *
 * <p>Fecha y hora siguen la convención del proyecto: la hora de la cita es la hora de reloj de la
 * clínica guardada en UTC, así que se formatea en UTC (10:00Z → "10:00 a.m.", mismo formato que la app).
 */
public final class RecordatorioPlantilla {

    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_MX);

    public static final String CUERPO =
            "Hola %s, te recordamos tu cita con %s el %s a las %s en %s. Responde 1 para confirmar o 2 para cancelar.";

    private RecordatorioPlantilla() {}

    public static List<String> parametros(Cita cita) {
        return List.of(
                valor(cita.getPaciente() != null ? cita.getPaciente().getNombre() : null, "paciente"),
                valor(cita.getMedico() != null ? cita.getMedico().getNombre() : null, "tu médico"),
                fecha(cita.getFechaHora()),
                hora(cita.getFechaHora()),
                valor(cita.getConsultorio() != null ? cita.getConsultorio().getNombre() : null, "el consultorio"));
    }

    public static String renderizar(List<String> params) {
        return String.format(CUERPO, params.toArray());
    }

    /** Canal "enlace": mismo recordatorio, pero el paciente responde en la página pública. */
    public static final String CUERPO_ENLACE =
            "Hola %s, te recordamos tu cita con %s el %s a las %s en %s. Confírmala o cancélala aquí: %s";

    /** Textos que se registran como mensaje ENTRANTE cuando el paciente responde desde el enlace. */
    public static final String CONFIRMO_ENLACE = "Confirmó desde el enlace";
    public static final String CANCELO_ENLACE = "Canceló desde el enlace";

    public static String conEnlace(Cita cita, String url) {
        List<String> p = parametros(cita);
        return String.format(CUERPO_ENLACE, p.get(0), p.get(1), p.get(2), p.get(3), p.get(4), url);
    }

    public static String confirmada(Cita cita) {
        return "¡Gracias! Tu cita del " + fecha(cita.getFechaHora()) + " a las " + hora(cita.getFechaHora())
                + " quedó confirmada. Te esperamos.";
    }

    public static String cancelada(Cita cita) {
        return "Tu cita del " + fecha(cita.getFechaHora()) + " a las " + hora(cita.getFechaHora())
                + " fue cancelada. Si deseas reagendar, comunícate con la clínica.";
    }

    public static String ayuda() {
        return "No entendimos tu respuesta. Responde 1 para confirmar o 2 para cancelar tu cita.";
    }

    static String fecha(OffsetDateTime fechaHora) {
        return FECHA.format(fechaHora.withOffsetSameInstant(ZoneOffset.UTC));
    }

    /** "9:00 a.m." (igual que la app web) — formateo manual para no depender de la versión de CLDR del JDK. */
    static String hora(OffsetDateTime fechaHora) {
        OffsetDateTime f = fechaHora.withOffsetSameInstant(ZoneOffset.UTC);
        int h = f.getHour() % 12 == 0 ? 12 : f.getHour() % 12;
        return String.format("%d:%02d %s", h, f.getMinute(), f.getHour() < 12 ? "a.m." : "p.m.");
    }

    private static String valor(String v, String porDefecto) {
        return v == null || v.isBlank() ? porDefecto : v;
    }
}
