package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class SolicitudEstudioRequest {

    @NotNull
    private UUID organizacionId;

    @NotNull
    private UUID pacienteId;

    @NotNull
    private UUID citaId;

    @NotNull
    private UUID subidoPorId;

    @NotBlank
    @Size(max = 30)
    private String tipo;

    public UUID getOrganizacionId() { return organizacionId; }
    public void setOrganizacionId(UUID organizacionId) { this.organizacionId = organizacionId; }

    public UUID getPacienteId() { return pacienteId; }
    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }

    public UUID getCitaId() { return citaId; }
    public void setCitaId(UUID citaId) { this.citaId = citaId; }

    public UUID getSubidoPorId() { return subidoPorId; }
    public void setSubidoPorId(UUID subidoPorId) { this.subidoPorId = subidoPorId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
