package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.PacienteRequest;
import com.clinica.mi_app.dto.response.PacienteResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.PacienteMapper;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PacienteRepository;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private final PacienteRepository repo;
    private final OrganizacionRepository orgRepo;

    public PacienteService(PacienteRepository repo, OrganizacionRepository orgRepo) {
        this.repo = repo;
        this.orgRepo = orgRepo;
    }

    public List<PacienteResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream().map(PacienteMapper::toResponse).collect(Collectors.toList());
    }

    public List<PacienteResponse> listarActivosPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionIdAndActivoTrue(organizacionId).stream().map(PacienteMapper::toResponse).collect(Collectors.toList());
    }

    public List<PacienteResponse> buscarPorNombre(UUID organizacionId, String nombre) {
        return repo.findByOrganizacionIdAndNombreContainingIgnoreCase(organizacionId, nombre)
                .stream().map(PacienteMapper::toResponse).collect(Collectors.toList());
    }

    public PacienteResponse buscarPorId(UUID id) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            if (!AuthenticatedUser.getEmail().equals(p.getEmail())) {
                throw new ResourceNotFoundException("Paciente", id.toString());
            }
        }
        return PacienteMapper.toResponse(p);
    }

    public PacienteResponse crear(PacienteRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Paciente p = new Paciente();
        p.setOrganizacion(org);
        p.setNombre(req.getNombre());
        p.setTelefono(req.getTelefono());
        p.setFechaNacimiento(req.getFechaNacimiento());
        p.setSexo(req.getSexo());
        p.setEmail(req.getEmail());
        p.setNotas(req.getNotas());
        return PacienteMapper.toResponse(repo.save(p));
    }

    public PacienteResponse actualizar(UUID id, PacienteRequest req) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        p.setNombre(req.getNombre());
        p.setTelefono(req.getTelefono());
        p.setFechaNacimiento(req.getFechaNacimiento());
        p.setSexo(req.getSexo());
        p.setEmail(req.getEmail());
        p.setNotas(req.getNotas());
        return PacienteMapper.toResponse(repo.save(p));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public PacienteResponse toggleActivo(UUID id) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        p.setActivo(!Boolean.TRUE.equals(p.getActivo()));
        return PacienteMapper.toResponse(repo.save(p));
    }
}
