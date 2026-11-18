package com.clinica.mi_app.whatsapp;

/** Normalización de teléfonos móviles de México. */
public final class TelefonoMx {

    private TelefonoMx() {}

    /**
     * Devuelve los 10 dígitos nacionales o {@code null} si no es un número MX reconocible.
     * Acepta "9511234567", "+52 951 123 4567", "529511234567", "5219511234567" (formato antiguo
     * de móviles que todavía envían Meta y Twilio) y prefijos como "whatsapp:+52...".
     */
    public static String aDiezDigitos(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 13 && d.startsWith("521")) return d.substring(3);
        if (d.length() == 12 && d.startsWith("52")) return d.substring(2);
        if (d.length() == 10) return d;
        return null;
    }

    /** "52" + 10 dígitos (formato de Meta Cloud API), o {@code null}. */
    public static String aInternacional(String raw) {
        String d = aDiezDigitos(raw);
        return d == null ? null : "52" + d;
    }

    /** "+52" + 10 dígitos (E.164, formato de Twilio), o {@code null}. */
    public static String aE164(String raw) {
        String d = aDiezDigitos(raw);
        return d == null ? null : "+52" + d;
    }
}
