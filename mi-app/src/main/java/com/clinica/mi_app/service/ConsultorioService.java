package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.ConsultorioRequest;
import com.clinica.mi_app.dto.response.ConsultorioResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.ConsultorioMapper;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.ConsultorioRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConsultorioService {

    private final ConsultorioRepository repo;
    private final OrganizacionRepository orgRepo;

    public ConsultorioService(ConsultorioRepository repo, OrganizacionRepository orgRepo) {
        this.repo = repo;
        this.orgRepo = orgRepo;
    }

    public List<ConsultorioResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream().map(ConsultorioMapper::toResponse).collect(Collectors.toList());
    }

    public List<ConsultorioResponse> listarActivosPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionIdAndActivoTrue(organizacionId).stream().map(ConsultorioMapper::toResponse).collect(Collectors.toList());
    }

    public ConsultorioResponse buscarPorId(UUID id) {
        return repo.findById(id).map(ConsultorioMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Consultorio", id.toString()));
    }

    public ConsultorioResponse crear(ConsultorioRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Consultorio c = new Consultorio();
        c.setOrganizacion(org);
        c.setNombre(req.getNombre());
        c.setDireccion(req.getDireccion());
        c.setTelefono(req.getTelefono());
        return ConsultorioMapper.toResponse(repo.save(c));
    }

    public ConsultorioResponse actualizar(UUID id, ConsultorioRequest req) {
        Consultorio c = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultorio", id.toString()));
        c.setNombre(req.getNombre());
        c.setDireccion(req.getDireccion());
        c.setTelefono(req.getTelefono());
        return ConsultorioMapper.toResponse(repo.save(c));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public ConsultorioResponse toggleActivo(UUID id) {
        Consultorio c = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultorio", id.toString()));
        c.setActivo(!Boolean.TRUE.equals(c.getActivo()));
        return ConsultorioMapper.toResponse(repo.save(c));
    }
}
