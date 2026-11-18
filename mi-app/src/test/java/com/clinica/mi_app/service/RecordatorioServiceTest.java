package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.exception.ConflictException;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.NotificacionRepository;
import com.clinica.mi_app.model.CitaEnlace;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RecordatorioServiceTest {

    @Mock private CitaRepository citaRepo;
    @Mock private NotificacionRepository notificacionRepo;
    @Mock private WhatsAppMensajeria mensajeria;
    @Mock private CitaEnlaceService enlaceService;

    /** ahora (reloj de la clínica en UTC) = 2026-11-10T10:00Z */
    private final ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));
    private RecordatorioService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID medicoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new RecordatorioService(citaRepo, notificacionRepo, mensajeria, reloj, enlaceService);
        when(notificacionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificacionRepo.findByCitaId(any())).thenReturn(List.of());
    }

    @Test
    void manual_enviado_registraNotificacionEnviado() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.1"));

        Notificacion n = service.enviarManual(orgId, null, cita.getId(), false);

        assertEquals("RECORDATORIO_MANUAL", n.getTipo());
        assertEquals("ENVIADO", n.getEstado());
        assertEquals("WHATSAPP", n.getCanal());
        assertNotNull(n.getEnviadaEn());
        assertSame(cita, n.getCita());
    }

    @Test
    void enlace_generaTokenYDevuelveWaMeConElTextoYLaUrl() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(mensajeria.proveedor()).thenReturn("enlace");
        String url = "http://192.168.0.10:5173/c/abc";
        when(enlaceService.generar(cita)).thenReturn(new CitaEnlaceService.Generado("abc", url, new CitaEnlace()));
        when(mensajeria.enviarTexto(eq(cita.getOrganizacion()), eq(cita), eq("9511234567"), anyString()))
                .thenReturn(Optional.of("enlace-1"));

        RecordatorioService.EnvioRecordatorio envio = service.enviarManualDetalle(orgId, null, cita.getId(), false);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(mensajeria).enviarTexto(any(), eq(cita), eq("9511234567"), texto.capture());
        assertTrue(texto.getValue().startsWith("Hola Ana Ramírez, te recordamos tu cita"));
        assertTrue(texto.getValue().endsWith("Confírmala o cancélala aquí: " + url));
        verify(mensajeria, never()).enviarRecordatorio(any());

        assertEquals("ENVIADO", envio.notificacion().getEstado());
        assertEquals("RECORDATORIO_MANUAL", envio.notificacion().getTipo());
        assertTrue(envio.waUrl().startsWith("https://wa.me/529511234567?text="));
        String decodificado = URLDecoder.decode(envio.waUrl().substring(envio.waUrl().indexOf("=") + 1), StandardCharsets.UTF_8);
        assertEquals(texto.getValue(), decodificado);
        assertFalse(envio.waUrl().contains("+"), "los espacios van como %20");
    }

    @Test
    void otrosProveedores_noGeneranEnlaceNiWaUrl() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(mensajeria.proveedor()).thenReturn("simulado");
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.9"));

        assertNull(service.enviarManualDetalle(orgId, null, cita.getId(), false).waUrl());
        verifyNoInteractions(enlaceService);
    }

    @Test
    void enlace_citaDeOtraOrganizacion_404_sinGenerarToken() {
        Cita ajena = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", UUID.randomUUID());
        when(citaRepo.findById(ajena.getId())).thenReturn(Optional.of(ajena));
        when(mensajeria.proveedor()).thenReturn("enlace");

        assertThrows(ResourceNotFoundException.class, () -> service.enviarManualDetalle(orgId, null, ajena.getId(), false));
        verifyNoInteractions(enlaceService);
    }

    @Test
    void manual_proveedorFalla_registraFallido() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.empty());

        Notificacion n = service.enviarManual(orgId, null, cita.getId(), false);

        assertEquals("FALLIDO", n.getEstado());
        assertNull(n.getEnviadaEn());
        verify(notificacionRepo).save(n);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADA", "NO_ASISTIO", "REAGENDADA"})
    void manual_estadoNoEnviable_400(String estado) {
        Cita cita = cita(estado, "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));

        assertThrows(IllegalArgumentException.class, () -> service.enviarManual(orgId, null, cita.getId(), false));
        verifyNoInteractions(mensajeria);
    }

    @Test
    void manual_citaPasadaSegunRelojDeLaClinica_400() {
        // 09:00Z < ahora 10:00Z (hora de reloj): ya pasó aunque en UTC "real" faltarían horas
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-10T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));

        assertThrows(IllegalArgumentException.class, () -> service.enviarManual(orgId, null, cita.getId(), false));
    }

    @Test
    void manual_pacienteSinTelefonoValido_400() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        cita.getPaciente().setTelefono("123");
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.enviarManual(orgId, null, cita.getId(), false));
        assertTrue(ex.getMessage().contains("teléfono"));
    }

    @Test
    void manual_yaEnviado_sinReenviar_409_conReenviar_envia() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(notificacionRepo.findByCitaId(cita.getId())).thenReturn(List.of(notif("RECORDATORIO_24H", "ENVIADO")));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.2"));

        assertThrows(ConflictException.class, () -> service.enviarManual(orgId, null, cita.getId(), false));
        verifyNoInteractions(mensajeria);

        assertEquals("ENVIADO", service.enviarManual(orgId, null, cita.getId(), true).getEstado());
    }

    @Test
    void manual_soloFallidoPrevio_noCuentaComoEnviado() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(notificacionRepo.findByCitaId(cita.getId())).thenReturn(List.of(notif("RECORDATORIO_MANUAL", "FALLIDO")));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.3"));

        assertEquals("ENVIADO", service.enviarManual(orgId, null, cita.getId(), false).getEstado());
    }

    @Test
    void manual_citaDeOtraOrganizacion_404() {
        Cita ajena = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", UUID.randomUUID());
        when(citaRepo.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThrows(ResourceNotFoundException.class, () -> service.enviarManual(orgId, null, ajena.getId(), false));
        verifyNoInteractions(mensajeria);
    }

    @Test
    void manual_medicoSoloSusCitas() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.4"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.enviarManual(orgId, UUID.randomUUID(), cita.getId(), false));
        assertEquals("ENVIADO", service.enviarManual(orgId, medicoId, cita.getId(), false).getEstado());
    }

    @Test
    void automatico_noDuplicaElMismoTipo_peroSiOtroTipo() {
        Cita cita = cita("CONFIRMADA", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));
        when(notificacionRepo.findByCitaId(cita.getId())).thenReturn(List.of(notif("RECORDATORIO_48H", "ENVIADO")));
        when(mensajeria.enviarRecordatorio(cita)).thenReturn(Optional.of("wamid.SIM.5"));

        assertTrue(service.enviarAutomatico(cita.getId(), "RECORDATORIO_48H").isEmpty());
        assertEquals("RECORDATORIO_24H", service.enviarAutomatico(cita.getId(), "RECORDATORIO_24H").orElseThrow().getTipo());
        verify(mensajeria, times(1)).enviarRecordatorio(cita);
    }

    @Test
    void automatico_estadoNoEnviable_noLanzaNiEnvia() {
        Cita cita = cita("NO_ASISTIO", "2026-11-11T09:00:00Z", orgId);
        when(citaRepo.findById(cita.getId())).thenReturn(Optional.of(cita));

        assertTrue(service.enviarAutomatico(cita.getId(), "RECORDATORIO_24H").isEmpty());
        verifyNoInteractions(mensajeria);
    }

    private Cita cita(String estado, String fechaHora, UUID org) {
        Organizacion o = new Organizacion();
        o.setId(org);
        Medico m = new Medico();
        m.setId(medicoId);
        Paciente p = new Paciente();
        p.setNombre("Ana Ramírez");
        p.setTelefono("9511234567");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(o);
        c.setMedico(m);
        c.setPaciente(p);
        c.setEstado(estado);
        c.setFechaHora(OffsetDateTime.parse(fechaHora));
        return c;
    }

    private static Notificacion notif(String tipo, String estado) {
        Notificacion n = new Notificacion();
        n.setTipo(tipo);
        n.setEstado(estado);
        return n;
    }
}
