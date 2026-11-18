package com.clinica.mi_app.controller;

import com.clinica.mi_app.config.SecurityConfig;
import com.clinica.mi_app.exception.ConflictException;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.security.JwtFilter;
import com.clinica.mi_app.security.JwtUtil;
import com.clinica.mi_app.service.RecordatorioService;
import com.clinica.mi_app.service.RecordatorioService.EnvioRecordatorio;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.clinica.mi_app.controller.AdminDashboardControllerTest.como;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecordatorioController.class)
@Import(SecurityConfig.class)
class RecordatorioControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtFilter jwtFilter;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RecordatorioService service;
    @MockitoBean private WhatsAppMensajeria mensajeria;

    private static final UUID ORG = UUID.fromString("b03f2637-d855-4667-8319-c78514dcadef");
    private final UUID citaId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.getArgument(2, jakarta.servlet.FilterChain.class).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(mensajeria.proveedor()).thenReturn("simulado");
    }

    static RequestPostProcessor medico(UUID orgId, UUID medicoId) {
        var auth = new UsernamePasswordAuthenticationToken("medico@clinica.mx", null,
                List.of(new SimpleGrantedAuthority("ROLE_MEDICO")));
        auth.setDetails(Map.of("rol", "MEDICO", "organizacionId", orgId, "medicoId", medicoId, "userId", UUID.randomUUID()));
        return authentication(auth);
    }

    private Notificacion notif(String estado) {
        Cita c = new Cita();
        c.setId(citaId);
        Notificacion n = new Notificacion();
        n.setId(UUID.randomUUID());
        n.setCita(c);
        n.setTipo("RECORDATORIO_MANUAL");
        n.setEstado(estado);
        n.setEnviadaEn("ENVIADO".equals(estado) ? OffsetDateTime.now() : null);
        return n;
    }

    private EnvioRecordatorio envio(String estado, String waUrl) {
        return new EnvioRecordatorio(notif(estado), waUrl);
    }

    @Test
    void canalEnlace_devuelveWaUrl() throws Exception {
        when(mensajeria.proveedor()).thenReturn("enlace");
        String waUrl = "https://wa.me/529511234567?text=Hola";
        when(service.enviarManualDetalle(ORG, null, citaId, false)).thenReturn(envio("ENVIADO", waUrl));

        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("enlace"))
                .andExpect(jsonPath("$.waUrl").value(waUrl));
    }

    @Test
    void admin_enviado_200() throws Exception {
        when(service.enviarManualDetalle(ORG, null, citaId, false)).thenReturn(envio("ENVIADO", null));

        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.tipo").value("RECORDATORIO_MANUAL"))
                .andExpect(jsonPath("$.proveedor").value("simulado"));
    }

    @Test
    void medico_pasaSuMedicoIdYReenviar() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(service.enviarManualDetalle(ORG, medicoId, citaId, true)).thenReturn(envio("ENVIADO", null));

        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).param("reenviar", "true")
                        .with(medico(ORG, medicoId)))
                .andExpect(status().isOk());
        verify(service).enviarManualDetalle(ORG, medicoId, citaId, true);
    }

    @Test
    void proveedorFalla_502ConFallido() throws Exception {
        when(service.enviarManualDetalle(eq(ORG), isNull(), eq(citaId), anyBoolean())).thenReturn(envio("FALLIDO", null));

        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.estado").value("FALLIDO"));
    }

    @Test
    void yaEnviado_409_citaAjena_404_cancelada_400() throws Exception {
        when(service.enviarManualDetalle(any(), any(), eq(citaId), anyBoolean()))
                .thenThrow(new ConflictException("Ya se envió"))
                .thenThrow(new ResourceNotFoundException("Cita", citaId.toString()))
                .thenThrow(new IllegalArgumentException("No se envían recordatorios a citas en estado CANCELADA"));

        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("ADMIN", ORG)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paciente_403() throws Exception {
        mockMvc.perform(post("/api/admin/recordatorios/{id}/enviar", citaId).with(como("PACIENTE", ORG)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
