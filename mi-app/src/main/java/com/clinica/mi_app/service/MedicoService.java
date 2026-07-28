package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.MedicoRequest;
import com.clinica.mi_app.dto.response.MedicoPublicoResponse;
import com.clinica.mi_app.dto.response.MedicoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.MedicoMapper;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.ConsultorioRepository;
import com.clinica.mi_app.repository.MedicoRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MedicoService {

    private final MedicoRepository repo;
    private final OrganizacionRepository orgRepo;
    private final ConsultorioRepository consultorioRepo;

    public MedicoService(MedicoRepository repo, OrganizacionRepository orgRepo, ConsultorioRepository consultorioRepo) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.consultorioRepo = consultorioRepo;
    }

    public List<MedicoResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream().map(MedicoMapper::toResponse).collect(Collectors.toList());
    }

    public List<MedicoResponse> listarActivosPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionIdAndActivoTrue(organizacionId).stream().map(MedicoMapper::toResponse).collect(Collectors.toList());
    }

    public List<MedicoPublicoResponse> listarPublicosPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionIdAndActivoTrue(organizacionId).stream().map(MedicoMapper::toPublicoResponse).collect(Collectors.toList());
    }

    public List<MedicoResponse> listarPorConsultorio(UUID consultorioId) {
        return repo.findByConsultorioId(consultorioId).stream().map(MedicoMapper::toResponse).collect(Collectors.toList());
    }

    public MedicoResponse buscarPorId(UUID id) {
        return repo.findById(id).map(MedicoMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Medico", id.toString()));
    }

    public MedicoResponse crear(MedicoRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Consultorio consultorio = consultorioRepo.findById(req.getConsultorioId())
                .orElseThrow(() -> new ResourceNotFoundException("Consultorio", req.getConsultorioId().toString()));
        Medico m = new Medico();
        m.setOrganizacion(org);
        m.setConsultorio(consultorio);
        m.setNombre(req.getNombre());
        m.setEspecialidad(req.getEspecialidad());
        m.setCedula(req.getCedula());
        m.setTelefono(req.getTelefono());
        m.setTarifaBase(req.getTarifaBase());
        return MedicoMapper.toResponse(repo.save(m));
    }

    public MedicoResponse actualizar(UUID id, MedicoRequest req) {
        Medico m = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medico", id.toString()));
        Consultorio consultorio = consultorioRepo.findById(req.getConsultorioId())
                .orElseThrow(() -> new ResourceNotFoundException("Consultorio", req.getConsultorioId().toString()));
        m.setConsultorio(consultorio);
        m.setNombre(req.getNombre());
        m.setEspecialidad(req.getEspecialidad());
        m.setCedula(req.getCedula());
        m.setTelefono(req.getTelefono());
        m.setTarifaBase(req.getTarifaBase());
        return MedicoMapper.toResponse(repo.save(m));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public MedicoResponse toggleActivo(UUID id) {
        Medico m = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medico", id.toString()));
        m.setActivo(!Boolean.TRUE.equals(m.getActivo()));
        return MedicoMapper.toResponse(repo.save(m));
    }
}
