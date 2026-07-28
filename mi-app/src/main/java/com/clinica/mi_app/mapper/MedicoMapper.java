package com.clinica.mi_app.mapper;

import com.clinica.mi_app.dto.response.MedicoPublicoResponse;
import com.clinica.mi_app.dto.response.MedicoResponse;
import com.clinica.mi_app.model.Medico;

public class MedicoMapper {

    public static MedicoPublicoResponse toPublicoResponse(Medico m) {
        MedicoPublicoResponse r = new MedicoPublicoResponse();
        r.setId(m.getId());
        r.setNombre(m.getNombre());
        r.setEspecialidad(m.getEspecialidad());
        r.setTarifaBase(m.getTarifaBase());
        if (m.getConsultorio() != null) {
            r.setConsultorioId(m.getConsultorio().getId());
            r.setConsultorioNombre(m.getConsultorio().getNombre());
            r.setConsultorioDireccion(m.getConsultorio().getDireccion());
        }
        return r;
    }

    public static MedicoResponse toResponse(Medico m) {
        MedicoResponse r = new MedicoResponse();
        r.setId(m.getId());
        r.setOrganizacionId(m.getOrganizacion() != null ? m.getOrganizacion().getId() : null);
        r.setConsultorioId(m.getConsultorio() != null ? m.getConsultorio().getId() : null);
        r.setNombre(m.getNombre());
        r.setEspecialidad(m.getEspecialidad());
        r.setCedula(m.getCedula());
        r.setTelefono(m.getTelefono());
        r.setTarifaBase(m.getTarifaBase());
        r.setActivo(m.getActivo());
        return r;
    }
}
