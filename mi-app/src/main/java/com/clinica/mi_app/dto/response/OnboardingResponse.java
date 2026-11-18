package com.clinica.mi_app.dto.response;

import java.util.UUID;

public class OnboardingResponse {

    private UUID organizacionId;
    private String mensaje;

    public UUID getOrganizacionId() { return organizacionId; }
    public void setOrganizacionId(UUID organizacionId) { this.organizacionId = organizacionId; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
