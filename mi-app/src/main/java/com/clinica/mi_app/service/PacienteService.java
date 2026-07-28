package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private static final Logger log = LoggerFactory.getLogger(PacienteService.class);

    private final PacienteRepository repo;
    private final OrganizacionRepository orgRepo;
    private final AuditClient auditClient;

    public PacienteService(PacienteRepository repo, OrganizacionRepository orgRepo, AuditClient auditClient) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.auditClient = auditClient;
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
        Paciente saved = repo.save(p);
        try {
            auditClient.audit("PACIENTE_CREAR", "PACIENTE", saved.getId(), org.getId(), null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit PACIENTE_CREAR: {}", ex.getMessage());
        }
        return PacienteMapper.toResponse(saved);
    }

    @Transactional
    public PacienteResponse actualizar(UUID id, PacienteRequest req) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        p.setNombre(req.getNombre());
        p.setTelefono(req.getTelefono());
        p.setFechaNacimiento(req.getFechaNacimiento());
        p.setSexo(req.getSexo());
        p.setEmail(req.getEmail());
        p.setNotas(req.getNotas());
        Paciente saved = repo.save(p);
        try {
            auditClient.audit("PACIENTE_ACTUALIZAR", "PACIENTE", saved.getId(), saved.getOrganizacion().getId(), null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit PACIENTE_ACTUALIZAR: {}", ex.getMessage());
        }
        return PacienteMapper.toResponse(saved);
    }

    @Transactional
    public void eliminar(UUID id) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        UUID orgId = p.getOrganizacion().getId();
        repo.deleteById(id);
        try {
            auditClient.audit("PACIENTE_ELIMINAR", "PACIENTE", id, orgId, null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit PACIENTE_ELIMINAR: {}", ex.getMessage());
        }
    }

    @Transactional
    public PacienteResponse toggleActivo(UUID id) {
        Paciente p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id.toString()));
        boolean activoAnterior = Boolean.TRUE.equals(p.getActivo());
        p.setActivo(!activoAnterior);
        Paciente saved = repo.save(p);
        try {
            Map<String, Object> meta = baseMeta();
            meta.put("activoAnterior", activoAnterior);
            meta.put("activoNuevo", !activoAnterior);
            auditClient.audit("PACIENTE_TOGGLE_ACTIVO", "PACIENTE", saved.getId(), saved.getOrganizacion().getId(), null, meta);
        } catch (Exception ex) {
            log.warn("audit PACIENTE_TOGGLE_ACTIVO: {}", ex.getMessage());
        }
        return PacienteMapper.toResponse(saved);
    }

    private Map<String, Object> baseMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", AuthenticatedUser.getUserId());
        m.put("rol", AuthenticatedUser.getRol());
        m.put("email", AuthenticatedUser.getEmail());
        return m;
    }
}
