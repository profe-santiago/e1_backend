package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.LoginRequest;
import com.clinica.mi_app.dto.response.AuthResponse;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.model.Usuario;
import com.clinica.mi_app.repository.PacienteRepository;
import com.clinica.mi_app.repository.UsuarioRepository;
import com.clinica.mi_app.security.LocalTokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalAuthServiceTest {

    private static KeyPair keys;
    private static String privatePem;
    private static final String HASH = new BCryptPasswordEncoder().encode("secreta123");

    private UsuarioRepository usuarioRepo;
    private PacienteRepository pacienteRepo;
    private LocalAuthService service;
    private Organizacion org;

    @BeforeAll
    static void claves() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @BeforeEach
    void setup() {
        usuarioRepo = mock(UsuarioRepository.class);
        pacienteRepo = mock(PacienteRepository.class);
        service = new LocalAuthService(usuarioRepo, pacienteRepo, new LocalTokenIssuer(privatePem, "", 60_000));
        org = new Organizacion();
        org.setId(UUID.randomUUID());
        org.setNombre("Clínica Test");
    }

    private Usuario usuario(String rol) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setEmail("user@test.mx");
        u.setRol(rol);
        u.setPasswordHash(HASH);
        u.setActivo(true);
        u.setOrganizacion(org);
        return u;
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(keys.getPublic()).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void login_medico_emiteTokenRS256ConClaimsDeInToGlobeYContexto() {
        Usuario u = usuario("MEDICO");
        Medico m = new Medico();
        m.setId(UUID.randomUUID());
        m.setNombre("Dra. Prueba");
        u.setMedico(m);
        when(usuarioRepo.findByEmail("user@test.mx")).thenReturn(Optional.of(u));

        AuthResponse r = service.login(req("secreta123")).orElseThrow();

        assertEquals("MEDICO", r.getRol());
        assertEquals(m.getId(), r.getMedicoId());
        Claims c = parse(r.getToken());
        assertEquals(u.getId().toString(), c.getSubject());
        assertEquals("MEDICO", c.get("role", String.class));
        assertEquals("clinicas", c.get("systemId", String.class));
        assertEquals(org.getId().toString(), c.get("tenantId", String.class));
        assertEquals(org.getId().toString(), c.get("organizacionId", String.class));
        assertEquals(m.getId().toString(), c.get("medicoId", String.class));
        assertEquals("user@test.mx", c.get("email", String.class));
    }

    @Test
    void login_paciente_incluyePacienteId() {
        Usuario u = usuario("PACIENTE");
        Paciente p = new Paciente();
        p.setId(UUID.randomUUID());
        p.setNombre("Ana Paciente");
        when(usuarioRepo.findByEmail("user@test.mx")).thenReturn(Optional.of(u));
        when(pacienteRepo.findByOrganizacionIdAndEmail(org.getId(), "user@test.mx")).thenReturn(Optional.of(p));

        AuthResponse r = service.login(req("secreta123")).orElseThrow();

        assertEquals(p.getId(), r.getPacienteId());
        assertEquals("Ana Paciente", r.getNombre());
        assertEquals(p.getId().toString(), parse(r.getToken()).get("pacienteId", String.class));
    }

    @Test
    void login_passwordIncorrecta_vacio() {
        when(usuarioRepo.findByEmail("user@test.mx")).thenReturn(Optional.of(usuario("ADMIN")));
        assertTrue(service.login(req("otra")).isEmpty());
    }

    @Test
    void login_usuarioInactivo_vacio() {
        Usuario u = usuario("ADMIN");
        u.setActivo(false);
        when(usuarioRepo.findByEmail("user@test.mx")).thenReturn(Optional.of(u));
        assertTrue(service.login(req("secreta123")).isEmpty());
    }

    @Test
    void login_emailInexistente_vacio() {
        when(usuarioRepo.findByEmail(any())).thenReturn(Optional.empty());
        assertTrue(service.login(req("secreta123")).isEmpty());
    }

    @Test
    void issuer_sinClave_fallaAlArrancar() {
        assertThrows(IllegalStateException.class, () -> new LocalTokenIssuer("", "", 1000));
    }

    private LoginRequest req(String pass) {
        LoginRequest r = new LoginRequest();
        r.setEmail("user@test.mx");
        r.setPassword(pass);
        return r;
    }
}
