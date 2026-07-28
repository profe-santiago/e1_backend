package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CitaRequest {

    @NotNull
    private UUID organizacionId;

    @NotNull
    private UUID pacienteId;

    @NotNull
    private UUID medicoId;

    @NotNull
    private UUID consultorioId;

    @NotNull
    private OffsetDateTime fechaHora;

    @NotNull
    private Short duracionMin;

    @Size(max = 20)
    private String estado;

    private String motivo;

    public UUID getOrganizacionId() { return organizacionId; }
    public void setOrganizacionId(UUID organizacionId) { this.organizacionId = organizacionId; }

    public UUID getPacienteId() { return pacienteId; }
    public void setPacienteId(UUID pacienteId) { this.pacienteId = pacienteId; }

    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }

    public UUID getConsultorioId() { return consultorioId; }
    public void setConsultorioId(UUID consultorioId) { this.consultorioId = consultorioId; }

    public OffsetDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(OffsetDateTime fechaHora) { this.fechaHora = fechaHora; }

    public Short getDuracionMin() { return duracionMin; }
    public void setDuracionMin(Short duracionMin) { this.duracionMin = duracionMin; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
