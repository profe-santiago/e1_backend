package com.clinica.mi_app.dto.response;

import com.clinica.mi_app.model.Notificacion;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code waUrl}: enlace https://wa.me/52... para abrir en otra pestaña (solo canal "enlace"; si no, null). */
public record EnvioRecordatorioResponse(UUID notificacionId, UUID citaId, String tipo, String estado,
                                        OffsetDateTime enviadaEn, String proveedor, String waUrl) {

    public static EnvioRecordatorioResponse of(Notificacion n, String proveedor) {
        return of(n, proveedor, null);
    }

    public static EnvioRecordatorioResponse of(Notificacion n, String proveedor, String waUrl) {
        return new EnvioRecordatorioResponse(n.getId(), n.getCita().getId(), n.getTipo(), n.getEstado(),
                n.getEnviadaEn(), proveedor, waUrl);
    }
}
