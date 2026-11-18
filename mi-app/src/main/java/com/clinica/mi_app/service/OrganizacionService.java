package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.OrganizacionRequest;
import com.clinica.mi_app.dto.response.OrganizacionResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.OrganizacionMapper;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganizacionService {

    private final OrganizacionRepository repo;

    public OrganizacionService(OrganizacionRepository repo) {
        this.repo = repo;
    }

    public List<OrganizacionResponse> listarTodas() {
        return repo.findAll().stream().map(OrganizacionMapper::toResponse).collect(Collectors.toList());
    }

    public OrganizacionResponse buscarPorId(UUID id) {
        return repo.findById(id).map(OrganizacionMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Organización", id.toString()));
    }

    public OrganizacionResponse crear(OrganizacionRequest req) {
        Organizacion org = new Organizacion();
        org.setNombre(req.getNombre());
        org.setPlan(req.getPlan());
        org.setTrialHasta(req.getTrialHasta());
        return OrganizacionMapper.toResponse(repo.save(org));
    }

    public OrganizacionResponse actualizar(UUID id, OrganizacionRequest req) {
        Organizacion org = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organización", id.toString()));
        org.setNombre(req.getNombre());
        org.setPlan(req.getPlan());
        org.setTrialHasta(req.getTrialHasta());
        return OrganizacionMapper.toResponse(repo.save(org));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
