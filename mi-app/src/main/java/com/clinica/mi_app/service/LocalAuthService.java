package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.LoginRequest;
import com.clinica.mi_app.dto.response.AuthResponse;
import com.clinica.mi_app.model.Usuario;
import com.clinica.mi_app.repository.PacienteRepository;
import com.clinica.mi_app.repository.UsuarioRepository;
import com.clinica.mi_app.security.LocalTokenIssuer;
import com.clinica.mi_app.security.Roles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Autenticación local (auth.mode=local): valida credenciales contra la tabla usuario
 * (BCrypt) y emite un token RS256 con LocalTokenIssuer. Sustituye temporalmente
 * al AuthService de InToGlobe mientras no esté disponible.
 */
@Service
@ConditionalOnProperty(name = "auth.mode", havingValue = "local")
public class LocalAuthService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final LocalTokenIssuer tokenIssuer;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LocalAuthService(UsuarioRepository usuarioRepository,
                            PacienteRepository pacienteRepository,
                            LocalTokenIssuer tokenIssuer) {
        this.usuarioRepository = usuarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.tokenIssuer = tokenIssuer;
    }

    /** @return la respuesta de login, o vacío si las credenciales son inválidas. */
    @Transactional(readOnly = true)
    public Optional<AuthResponse> login(LoginRequest req) {
        Optional<Usuario> found = usuarioRepository.findByEmail(req.getEmail());
        if (found.isEmpty()) return Optional.empty();
        Usuario u = found.get();
        if (!Boolean.TRUE.equals(u.getActivo())
                || u.getPasswordHash() == null
                || !encoder.matches(req.getPassword(), u.getPasswordHash())) {
            return Optional.empty();
        }

        UUID orgId = u.getOrganizacion().getId();
        UUID medicoId = u.getMedico() != null ? u.getMedico().getId() : null;
        UUID pacienteId = null;
        String nombre = u.getEmail();
        if (u.getMedico() != null) {
            nombre = u.getMedico().getNombre();
        } else if (Roles.PACIENTE.equals(u.getRol())) {
            var paciente = pacienteRepository.findByOrganizacionIdAndEmail(orgId, u.getEmail());
            if (paciente.isPresent()) {
                pacienteId = paciente.get().getId();
                nombre = paciente.get().getNombre();
            }
        } else if (Roles.ADMIN.equals(u.getRol())) {
            nombre = "Administración · " + u.getOrganizacion().getNombre();
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", orgId);
        claims.put("organizacionId", orgId);
        claims.put("email", u.getEmail());
        claims.put("medicoId", medicoId);
        claims.put("pacienteId", pacienteId);
        String token = tokenIssuer.issue(u.getId().toString(), u.getRol(), claims);

        return Optional.of(new AuthResponse(token, u.getId(), u.getEmail(), nombre, u.getRol(), orgId, medicoId, pacienteId));
    }
}
