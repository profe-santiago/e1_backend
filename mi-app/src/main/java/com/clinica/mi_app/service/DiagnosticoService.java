package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.DiagnosticoRequest;
import com.clinica.mi_app.dto.response.DiagnosticoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.DiagnosticoMapper;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Diagnostico;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.DiagnosticoRepository;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DiagnosticoService {

    private final DiagnosticoRepository repo;
    private final CitaRepository citaRepo;

    public DiagnosticoService(DiagnosticoRepository repo, CitaRepository citaRepo) {
        this.repo = repo;
        this.citaRepo = citaRepo;
    }

    public List<DiagnosticoResponse> listarPorCita(UUID citaId) {
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            Cita cita = citaRepo.findById(citaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico", citaId.toString()));
            if (!AuthenticatedUser.getEmail().equals(cita.getPaciente().getEmail())) {
                throw new ResourceNotFoundException("Diagnóstico", citaId.toString());
            }
        }
        return repo.findByCitaId(citaId).stream().map(DiagnosticoMapper::toResponse).collect(Collectors.toList());
    }

    public List<DiagnosticoResponse> listarPorCitaYTipo(UUID citaId, String tipo) {
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            Cita cita = citaRepo.findById(citaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico", citaId.toString()));
            if (!AuthenticatedUser.getEmail().equals(cita.getPaciente().getEmail())) {
                throw new ResourceNotFoundException("Diagnóstico", citaId.toString());
            }
        }
        return repo.findByCitaIdAndTipo(citaId, tipo).stream().map(DiagnosticoMapper::toResponse).collect(Collectors.toList());
    }

    public DiagnosticoResponse buscarPorId(UUID id) {
        Diagnostico d = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico", id.toString()));
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            if (!AuthenticatedUser.getEmail().equals(d.getCita().getPaciente().getEmail())) {
                throw new ResourceNotFoundException("Diagnóstico", id.toString());
            }
        }
        return DiagnosticoMapper.toResponse(d);
    }

    public DiagnosticoResponse crear(DiagnosticoRequest req) {
        Cita cita = citaRepo.findById(req.getCitaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita", req.getCitaId().toString()));
        Diagnostico d = new Diagnostico();
        d.setCita(cita);
        d.setCodigoCie10(req.getCodigoCie10());
        d.setDescripcion(req.getDescripcion());
        d.setTipo(req.getTipo());
        return DiagnosticoMapper.toResponse(repo.save(d));
    }

    public DiagnosticoResponse actualizar(UUID id, DiagnosticoRequest req) {
        Diagnostico d = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico", id.toString()));
        d.setCodigoCie10(req.getCodigoCie10());
        d.setDescripcion(req.getDescripcion());
        d.setTipo(req.getTipo());
        return DiagnosticoMapper.toResponse(repo.save(d));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
