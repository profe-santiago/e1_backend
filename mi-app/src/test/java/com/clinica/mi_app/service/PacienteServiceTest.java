package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.dto.request.PacienteRequest;
import com.clinica.mi_app.dto.response.PacienteResponse;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PacienteServiceTest {

    @Mock private PacienteRepository repo;
    @Mock private OrganizacionRepository orgRepo;
    @Mock private AuditClient auditClient;

    private PacienteService service;

    private UUID orgId;
    private UUID pacienteId;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new PacienteService(repo, orgRepo, auditClient);
        orgId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();

        var auth = new UsernamePasswordAuthenticationToken("admin@clinic.com", null, List.of());
        auth.setDetails(Map.of("rol", "ADMIN", "userId", UUID.randomUUID(), "organizacionId", orgId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Test 1: crear ──

    @Test
    public void testCrear_auditaConAccionCorrecta() {
        Organizacion org = org();
        Paciente saved = pacienteConOrg(org);
        PacienteRequest req = request(org.getId());

        when(orgRepo.findById(org.getId())).thenReturn(Optional.of(org));
        when(repo.save(any())).thenReturn(saved);

        PacienteResponse response = service.crear(req);

        assertNotNull(response);
        verify(auditClient).audit(eq("PACIENTE_CREAR"), eq("PACIENTE"), eq(pacienteId), eq(org.getId()), isNull(), anyMap());
    }

    // ── Test 2: actualizar ──

    @Test
    public void testActualizar_auditaConAccionCorrecta() {
        Organizacion org = org();
        Paciente existente = pacienteConOrg(org);
        PacienteRequest req = request(org.getId());

        when(repo.findById(pacienteId)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenReturn(existente);

        service.actualizar(pacienteId, req);

        verify(auditClient).audit(eq("PACIENTE_ACTUALIZAR"), eq("PACIENTE"), eq(pacienteId), eq(org.getId()), isNull(), anyMap());
    }

    // ── Test 3: eliminar ──

    @Test
    public void testEliminar_auditaConAccionCorrecta() {
        Organizacion org = org();
        Paciente existente = pacienteConOrg(org);

        when(repo.findById(pacienteId)).thenReturn(Optional.of(existente));

        service.eliminar(pacienteId);

        verify(repo).deleteById(pacienteId);
        verify(auditClient).audit(eq("PACIENTE_ELIMINAR"), eq("PACIENTE"), eq(pacienteId), eq(org.getId()), isNull(), anyMap());
    }

    // ── Test 4: toggleActivo ──

    @SuppressWarnings("unchecked")
    @Test
    public void testToggleActivo_auditaConMetadataActivo() {
        Organizacion org = org();
        Paciente existente = pacienteConOrg(org);
        existente.setActivo(true);

        when(repo.findById(pacienteId)).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenReturn(existente);

        service.toggleActivo(pacienteId);

        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).audit(eq("PACIENTE_TOGGLE_ACTIVO"), eq("PACIENTE"), any(), eq(org.getId()), isNull(), metaCaptor.capture());
        Map<String, Object> meta = metaCaptor.getValue();
        assertEquals(true, meta.get("activoAnterior"));
        assertEquals(false, meta.get("activoNuevo"));
    }

    // ── Test 5: excepción de audit no propaga ──

    @Test
    public void testCrear_auditLanzaExcepcion_operacionNoFalla() {
        Organizacion org = org();
        Paciente saved = pacienteConOrg(org);
        PacienteRequest req = request(org.getId());

        when(orgRepo.findById(org.getId())).thenReturn(Optional.of(org));
        when(repo.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("audit down")).when(auditClient)
                .audit(anyString(), anyString(), any(), any(), any(), anyMap());

        assertDoesNotThrow(() -> service.crear(req));
        verify(repo).save(any(Paciente.class));
    }

    // ── helpers ──

    private Organizacion org() {
        Organizacion o = new Organizacion();
        o.setId(orgId);
        return o;
    }

    private Paciente pacienteConOrg(Organizacion org) {
        Paciente p = new Paciente();
        p.setId(pacienteId);
        p.setOrganizacion(org);
        p.setNombre("Test Paciente");
        p.setActivo(true);
        return p;
    }

    private PacienteRequest request(UUID organizacionId) {
        PacienteRequest r = new PacienteRequest();
        r.setOrganizacionId(organizacionId);
        r.setNombre("Test Paciente");
        return r;
    }
}
