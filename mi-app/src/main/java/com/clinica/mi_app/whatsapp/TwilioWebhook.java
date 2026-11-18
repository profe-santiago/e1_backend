package com.clinica.mi_app.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Firma y parseo de los webhooks de Twilio (form-urlencoded). */
public final class TwilioWebhook {

    private TwilioWebhook() {}

    /**
     * X-Twilio-Signature = Base64(HMAC-SHA1(authToken, url + concat(nombre + valor) con los parámetros
     * POST ordenados por nombre)). {@code url} debe ser exactamente la registrada en Twilio.
     */
    public static String firmar(String url, Map<String, List<String>> params, String authToken) {
        StringBuilder datos = new StringBuilder(url);
        for (Map.Entry<String, List<String>> e : new TreeMap<>(params).entrySet()) {
            List<String> valores = new ArrayList<>(e.getValue() == null ? List.of() : e.getValue());
            valores.sort(null);
            for (String v : valores) datos.append(e.getKey()).append(v == null ? "" : v);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(datos.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular la firma de Twilio", ex);
        }
    }

    /** Falla cerrado: sin URL, token o firma, la petición no es válida. Comparación en tiempo constante. */
    public static boolean firmaValida(String url, Map<String, List<String>> params, String authToken, String firma) {
        if (url == null || url.isBlank() || authToken == null || authToken.isBlank() || firma == null || firma.isBlank()) {
            return false;
        }
        byte[] esperada = firmar(url, params, authToken).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * From "whatsapp:+5219511234567" → 10 dígitos. Texto: ButtonPayload si el paciente tocó un botón,
     * si no Body. Twilio no manda context.id, pero sí OriginalRepliedMessageSid cuando se responde citando.
     */
    public static MensajeEntrante mensaje(Map<String, List<String>> params) {
        return new MensajeEntrante(
                uno(params, "MessageSid"),
                TelefonoMx.aDiezDigitos(uno(params, "From")),
                primero(uno(params, "ButtonPayload"), uno(params, "Body")),
                uno(params, "OriginalRepliedMessageSid"));
    }

    public static EstadoEntrega estado(Map<String, List<String>> params) {
        return new EstadoEntrega(uno(params, "MessageSid"), EstadoEntrega.traducir(uno(params, "MessageStatus")));
    }

    private static String uno(Map<String, List<String>> params, String nombre) {
        List<String> v = params.get(nombre);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    private static String primero(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
