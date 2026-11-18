package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.WhatsAppMensaje;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Canal con el que SE ENVIÓ un recordatorio (no el canal configurado hoy): se busca el mensaje saliente
 * de la misma cita más cercano en tiempo y se mira su id de proveedor ("enlace-..." = canal enlace).
 */
public final class CanalRecordatorio {

    /** Tolerancia para asociar una notificación con su mensaje saliente (mismo envío). */
    static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private CanalRecordatorio() {}

    /** Ids de las notificaciones cuyo mensaje saliente se preparó para wa.me (canal "enlace"). */
    public static Set<UUID> porEnlace(Collection<Notificacion> recordatorios, Collection<WhatsAppMensaje> salientes) {
        Map<UUID, List<WhatsAppMensaje>> porCita = salientes.stream()
                .filter(m -> m.getCita() != null && m.getCreatedAt() != null)
                .collect(Collectors.groupingBy(m -> m.getCita().getId()));
        Set<UUID> resultado = new HashSet<>();
        for (Notificacion n : recordatorios) {
            if (n.getCita() == null) continue;
            OffsetDateTime cuando = n.getEnviadaEn() != null ? n.getEnviadaEn() : n.getCreadaEn();
            if (cuando == null) continue;
            porCita.getOrDefault(n.getCita().getId(), List.of()).stream()
                    .min(Comparator.comparing(m -> Duration.between(m.getCreatedAt(), cuando).abs()))
                    .filter(m -> Duration.between(m.getCreatedAt(), cuando).abs().compareTo(TOLERANCIA) <= 0)
                    .filter(m -> m.getWamid() != null && m.getWamid().startsWith(EnlaceWhatsAppSender.PREFIJO_ID))
                    .ifPresent(m -> resultado.add(n.getId()));
        }
        return resultado;
    }
}
