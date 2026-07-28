package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AdjuntoRequest {

    @NotNull
    private UUID organizacionId;

    @NotNull
    private UUID pacienteId;

    private UUID citaId;

    @NotNull
    private UUID subidoPorId;

    @NotBlank
    @Size(max = 30)
    private String tipo;

    @Size(max = 200)
    private String nombreArchivo;

    private String urlArchivo;

    @Size(max = 80)
    private String mimeType;

    private Boolean notificar = false;

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

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getUrlArchivo() { return urlArchivo; }
    public void setUrlArchivo(String urlArchivo) { this.urlArchivo = urlArchivo; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Boolean getNotificar() { return notificar; }
    public void setNotificar(Boolean notificar) { this.notificar = notificar; }
}
