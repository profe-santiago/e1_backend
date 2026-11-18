package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.dto.response.ConversacionResumenResponse;
import com.clinica.mi_app.dto.response.RecordatorioEstadoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WhatsAppConsultaServiceTest {

    @Mock private WhatsAppMensajeRepository repo;
    @Mock private CitaRepository citaRepo;
    @Mock private NotificacionRepository notificacionRepo;
    private WhatsAppConsultaService service;

    private final Organizacion orgA = org();
    private final Organizacion orgB = org();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new WhatsAppConsultaService(repo, citaRepo, notificacionRepo);
    }

    @Test
    void mensajesPorCita_deOtraOrganizacion_404() {
        Cita ajena = cita(orgB);
        when(citaRepo.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThrows(ResourceNotFoundException.class, () -> service.mensajes(orgA.getId(), ajena.getId(), null));
        verify(repo, never()).findByOrganizacionIdAndCitaIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void mensajesPorTelefono_siempreFiltraPorLaOrganizacionYNormaliza() {
        when(repo.findByOrganizacionIdAndTelefonoOrderByCreatedAtAsc(orgA.getId(), "9511234567")).thenReturn(List.of());

        service.mensajes(orgA.getId(), null, "+52 1 951 123 4567");

        verify(repo).findByOrganizacionIdAndTelefonoOrderByCreatedAtAsc(orgA.getId(), "9511234567");
        verify(repo, never()).findByTelefonoAndDireccionOrderByCreatedAtDesc(anyString(), anyString());
    }

    @Test
    void mensajes_sinFiltro_400() {
        assertThrows(IllegalArgumentException.class, () -> service.mensajes(orgA.getId(), null, null));
    }

    @Test
    void resumen_unaConversacionPorTelefono_masRecientePrimero() {
        Cita c1 = cita(orgA);
        when(repo.findTop500ByOrganizacionIdOrderByCreatedAtDesc(orgA.getId())).thenReturn(List.of(
                msg(c1, "9511111111", "ENTRANTE", "1", "2026-11-10T18:00:00Z"),
                msg(null, "9522222222", "SALIENTE", "Hola", "2026-11-10T17:00:00Z"),
                msg(c1, "9511111111", "SALIENTE", "Recordatorio", "2026-11-10T16:00:00Z")));

        List<ConversacionResumenResponse> r = service.resumen(orgA.getId());

        assertEquals(2, r.size());
        assertEquals("9511111111", r.get(0).telefono());
        assertEquals("1", r.get(0).ultimoTexto());
        assertEquals("Ana Ramírez", r.get(0).pacienteNombre());
        assertEquals(2, r.get(0).mensajes());
        assertNull(r.get(1).pacienteNombre());
    }

    @Test
    void estadoRecordatorios_respondioSiHayEntranteDespuesDelRecordatorio() {
        Cita respondida = cita(orgA);
        Cita enviada = cita(orgA);
        Cita fallida = cita(orgA);
        UUID sinNada = UUID.randomUUID();
        when(notificacionRepo.findByOrganizacionIdAndCitaIdIn(any(), any())).thenReturn(List.of(
                notif(respondida, "RECORDATORIO_MANUAL", "ENVIADO", "2026-11-10T16:00:00Z"),
                notif(enviada, "RECORDATORIO_24H", "ENVIADO", "2026-11-10T16:00:00Z"),
                notif(fallida, "RECORDATORIO_MANUAL", "FALLIDO", "2026-11-10T16:00:00Z"),
                notif(enviada, "CANCELACION_PACIENTE", "ENVIADO", "2026-11-10T19:00:00Z")));
        when(repo.findByOrganizacionIdAndCitaIdInAndDireccion(any(), any(), any())).thenReturn(List.of(
                msg(respondida, "9511111111", "ENTRANTE", "1", "2026-11-10T16:05:00Z")));

        Map<UUID, RecordatorioEstadoResponse> r = service.estadoRecordatorios(orgA.getId(),
                List.of(respondida.getId(), enviada.getId(), fallida.getId(), sinNada));

        assertEquals("RESPONDIO", r.get(respondida.getId()).estado());
        assertEquals("1", r.get(respondida.getId()).respuesta());
        assertEquals("ENVIADO", r.get(enviada.getId()).estado());
        assertEquals("FALLIDO", r.get(fallida.getId()).estado());
        assertFalse(r.containsKey(sinNada));
        verify(notificacionRepo).findByOrganizacionIdAndCitaIdIn(eqOrg(), any());
    }

    private UUID eqOrg() {
        return org.mockito.ArgumentMatchers.eq(orgA.getId());
    }

    private static Organizacion org() {
        Organizacion o = new Organizacion();
        o.setId(UUID.randomUUID());
        return o;
    }

    private static Cita cita(Organizacion org) {
        Paciente p = new Paciente();
        p.setNombre("Ana Ramírez");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(org);
        c.setPaciente(p);
        return c;
    }

    private static WhatsAppMensaje msg(Cita cita, String tel, String dir, String texto, String fecha) {
        WhatsAppMensaje m = new WhatsAppMensaje();
        m.setId(UUID.randomUUID());
        m.setCita(cita);
        m.setTelefono(tel);
        m.setDireccion(dir);
        m.setTexto(texto);
        m.setEstado("ENTRANTE".equals(dir) ? "RECIBIDO" : "ENVIADO");
        m.setCreatedAt(OffsetDateTime.parse(fecha));
        return m;
    }

    private static Notificacion notif(Cita cita, String tipo, String estado, String fecha) {
        Notificacion n = new Notificacion();
        n.setCita(cita);
        n.setTipo(tipo);
        n.setEstado(estado);
        n.setCreadaEn(OffsetDateTime.parse(fecha));
        return n;
    }
}
