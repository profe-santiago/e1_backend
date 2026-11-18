package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.response.CampanaResponse;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.AdjuntoRepository;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La campana muestra cancelaciones y confirmaciones del paciente, siempre acotada a la organización. */
class NotificacionCampanaServiceTest {

    @Mock private NotificacionRepository repo;
    @Mock private OrganizacionRepository orgRepo;
    @Mock private CitaRepository citaRepo;
    @Mock private AdjuntoRepository adjuntoRepo;

    private NotificacionService service;
    private final UUID orgId = UUID.randomUUID();
    private static final List<String> TIPOS = List.of("CANCELACION_PACIENTE", "CONFIRMACION_PACIENTE");

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new NotificacionService(repo, orgRepo, citaRepo, adjuntoRepo);
    }

    @Test
    void admin_incluyeConfirmacionesYCancelaciones_deSuOrganizacion() {
        when(repo.findTop20ByOrganizacionIdAndTipoInOrderByCreadaEnDesc(orgId, TIPOS))
                .thenReturn(List.of(notif("CONFIRMACION_PACIENTE"), notif("CANCELACION_PACIENTE")));
        when(repo.countByOrganizacionIdAndTipoInAndLeidaFalse(orgId, TIPOS)).thenReturn(2L);

        CampanaResponse r = service.campana(orgId, null);

        assertEquals(2, r.noLeidas());
        assertEquals(List.of("CONFIRMACION_PACIENTE", "CANCELACION_PACIENTE"),
                r.items().stream().map(i -> i.tipo()).toList());
        verify(repo).findTop20ByOrganizacionIdAndTipoInOrderByCreadaEnDesc(orgId, TIPOS);
    }

    @Test
    void medico_soloSusCitas() {
        UUID medicoId = UUID.randomUUID();
        when(repo.findTop20ByOrganizacionIdAndTipoInAndCita_Medico_IdOrderByCreadaEnDesc(orgId, TIPOS, medicoId))
                .thenReturn(List.of());

        service.campana(orgId, medicoId);

        verify(repo).findTop20ByOrganizacionIdAndTipoInAndCita_Medico_IdOrderByCreadaEnDesc(orgId, TIPOS, medicoId);
        verify(repo).countByOrganizacionIdAndTipoInAndCita_Medico_IdAndLeidaFalse(orgId, TIPOS, medicoId);
        verify(repo, never()).findTop20ByOrganizacionIdAndTipoInOrderByCreadaEnDesc(any(), any());
    }

    @Test
    void marcarTodas_incluyeConfirmaciones() {
        Notificacion a = notif("CONFIRMACION_PACIENTE");
        when(repo.findByOrganizacionIdAndTipoInAndLeidaFalse(orgId, TIPOS)).thenReturn(List.of(a));

        assertEquals(1, service.marcarTodasLeidas(orgId, null));
        assertTrue(a.getLeida());
    }

    private static Notificacion notif(String tipo) {
        Paciente p = new Paciente();
        p.setNombre("Ana Sofía Ramírez Mendoza");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setPaciente(p);
        Notificacion n = new Notificacion();
        n.setId(UUID.randomUUID());
        n.setTipo(tipo);
        n.setCita(c);
        return n;
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
