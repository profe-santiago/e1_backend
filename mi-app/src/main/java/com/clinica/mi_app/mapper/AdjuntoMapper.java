package com.clinica.mi_app.mapper;

import com.clinica.mi_app.dto.response.AdjuntoResponse;
import com.clinica.mi_app.model.Adjunto;

public class AdjuntoMapper {

    public static AdjuntoResponse toResponse(Adjunto a) {
        AdjuntoResponse r = new AdjuntoResponse();
        r.setId(a.getId());
        r.setOrganizacionId(a.getOrganizacion().getId());
        r.setPacienteId(a.getPaciente().getId());
        r.setCitaId(a.getCita() != null ? a.getCita().getId() : null);
        r.setSubidoPorId(a.getSubidoPor().getId());
        r.setTipo(a.getTipo());
        r.setNombreArchivo(a.getNombreArchivo());
        r.setUrlArchivo(a.getUrlArchivo());
        r.setMimeType(a.getMimeType());
        r.setNotificar(a.getNotificar());
        r.setNotificadoEn(a.getNotificadoEn());
        r.setEstado(a.getEstado());
        r.setComentarioRevision(a.getComentarioRevision());
        r.setRevisadoEn(a.getRevisadoEn());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}
