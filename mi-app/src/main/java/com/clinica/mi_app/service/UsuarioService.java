package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.UsuarioRequest;
import com.clinica.mi_app.dto.response.UsuarioResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.UsuarioMapper;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Usuario;
import com.clinica.mi_app.repository.MedicoRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final OrganizacionRepository orgRepo;
    private final MedicoRepository medicoRepo;
    private final BCryptPasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repo, OrganizacionRepository orgRepo,
                          MedicoRepository medicoRepo, BCryptPasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.medicoRepo = medicoRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream().map(UsuarioMapper::toResponse).collect(Collectors.toList());
    }

    public Optional<UsuarioResponse> buscarPorEmail(String email) {
        return repo.findByEmail(email).map(UsuarioMapper::toResponse);
    }

    public UsuarioResponse buscarPorId(UUID id) {
        return repo.findById(id).map(UsuarioMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id.toString()));
    }

    public UsuarioResponse crear(UsuarioRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización", req.getOrganizacionId().toString()));
        Usuario u = new Usuario();
        u.setOrganizacion(org);
        u.setEmail(req.getEmail());
        u.setRol(req.getRol());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        if (req.getMedicoId() != null) {
            Medico medico = medicoRepo.findById(req.getMedicoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Médico", req.getMedicoId().toString()));
            u.setMedico(medico);
        }
        return UsuarioMapper.toResponse(repo.save(u));
    }

    public UsuarioResponse actualizar(UUID id, UsuarioRequest req) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id.toString()));
        u.setEmail(req.getEmail());
        u.setRol(req.getRol());
        if (req.getMedicoId() != null) {
            Medico medico = medicoRepo.findById(req.getMedicoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Médico", req.getMedicoId().toString()));
            u.setMedico(medico);
        }
        return UsuarioMapper.toResponse(repo.save(u));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
