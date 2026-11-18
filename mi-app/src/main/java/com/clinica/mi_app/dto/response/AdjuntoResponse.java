package com.clinica.mi_app.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdjuntoResponse {

    private UUID id;
    private UUID organizacionId;
    private UUID pacienteId;
    private UUID citaId;
    private UUID subidoPorId;
    private String tipo;
    private String nombreArchivo;
    private String urlArchivo;
    private String mimeType;
    private Boolean notificar;
    private OffsetDateTime notificadoEn;
    private String estado;
    private String comentarioRevision;
    private OffsetDateTime revisadoEn;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public OffsetDateTime getNotificadoEn() { return notificadoEn; }
    public void setNotificadoEn(OffsetDateTime notificadoEn) { this.notificadoEn = notificadoEn; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getComentarioRevision() { return comentarioRevision; }
    public void setComentarioRevision(String comentarioRevision) { this.comentarioRevision = comentarioRevision; }

    public OffsetDateTime getRevisadoEn() { return revisadoEn; }
    public void setRevisadoEn(OffsetDateTime revisadoEn) { this.revisadoEn = revisadoEn; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
