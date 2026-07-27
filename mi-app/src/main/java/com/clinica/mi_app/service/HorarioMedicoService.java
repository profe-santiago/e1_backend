package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.HorarioMedicoRequest;
import com.clinica.mi_app.dto.response.HorarioMedicoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.HorarioMedicoMapper;
import com.clinica.mi_app.model.HorarioMedico;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.repository.HorarioMedicoRepository;
import com.clinica.mi_app.repository.MedicoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HorarioMedicoService {

    private final HorarioMedicoRepository repo;
    private final MedicoRepository medicoRepo;

    public HorarioMedicoService(HorarioMedicoRepository repo, MedicoRepository medicoRepo) {
        this.repo = repo;
        this.medicoRepo = medicoRepo;
    }

    public List<HorarioMedicoResponse> listarPorMedico(UUID medicoId) {
        return repo.findByMedicoId(medicoId).stream().map(HorarioMedicoMapper::toResponse).collect(Collectors.toList());
    }

    public List<HorarioMedicoResponse> listarPorMedicoYDia(UUID medicoId, Short diaSemana) {
        return repo.findByMedicoIdAndDiaSemana(medicoId, diaSemana).stream().map(HorarioMedicoMapper::toResponse).collect(Collectors.toList());
    }

    public HorarioMedicoResponse buscarPorId(UUID id) {
        return repo.findById(id).map(HorarioMedicoMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("HorarioMedico", id.toString()));
    }

    public HorarioMedicoResponse crear(HorarioMedicoRequest req) {
        Medico medico = medicoRepo.findById(req.getMedicoId())
                .orElseThrow(() -> new ResourceNotFoundException("Medico", req.getMedicoId().toString()));
        if (repo.existsByMedicoIdAndDiaSemana(req.getMedicoId(), req.getDiaSemana())) {
            throw new IllegalArgumentException("El médico ya tiene un horario registrado para este día.");
        }
        HorarioMedico h = new HorarioMedico();
        h.setMedico(medico);
        h.setDiaSemana(req.getDiaSemana());
        h.setHoraInicio(req.getHoraInicio());
        h.setHoraFin(req.getHoraFin());
        h.setDuracionConsulta(req.getDuracionConsulta());
        return HorarioMedicoMapper.toResponse(repo.save(h));
    }

    public HorarioMedicoResponse actualizar(UUID id, HorarioMedicoRequest req) {
        HorarioMedico h = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HorarioMedico", id.toString()));
        if (repo.existsByMedicoIdAndDiaSemanaAndIdNot(h.getMedico().getId(), req.getDiaSemana(), id)) {
            throw new IllegalArgumentException("El médico ya tiene un horario registrado para este día.");
        }
        h.setDiaSemana(req.getDiaSemana());
        h.setHoraInicio(req.getHoraInicio());
        h.setHoraFin(req.getHoraFin());
        h.setDuracionConsulta(req.getDuracionConsulta());
        return HorarioMedicoMapper.toResponse(repo.save(h));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
