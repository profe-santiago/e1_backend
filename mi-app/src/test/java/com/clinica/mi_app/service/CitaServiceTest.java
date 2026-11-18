package com.clinica.mi_app.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.request.CitaRequest;
import com.clinica.mi_app.dto.response.CitaResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.HorarioMedico;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.ConsultorioRepository;
import com.clinica.mi_app.repository.MedicoRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.HorarioMedicoRepository;
import com.clinica.mi_app.repository.PacienteRepository;

public class CitaServiceTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private OrganizacionRepository organizacionRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private ConsultorioRepository consultorioRepository;

    @Mock
    private HorarioMedicoRepository horarioMedicoRepository;

    @Mock
    private AuditClient auditClient;

    @Spy
    private ClinicaReloj clinicaReloj = new ClinicaReloj(java.time.Clock.systemUTC());

    @InjectMocks
    private CitaService citaService;

    private UUID organizacionId;
    private UUID pacienteId;
    private UUID medicoId;
    private UUID consultorioId;
    private UUID citaId;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        organizacionId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();
        medicoId = UUID.randomUUID();
        consultorioId = UUID.randomUUID();
        citaId = UUID.randomUUID();
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String rol, UUID userId, UUID orgId) {
        var auth = new UsernamePasswordAuthenticationToken("test@clinic.com", null, List.of());
        auth.setDetails(Map.of("rol", rol, "userId", userId, "organizacionId", orgId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void testCrearCita_Success() {
        // Arrange
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);

        OffsetDateTime fechaCita = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0); // fecha fija: evita fallos cuando "ahora + N h" cruza medianoche UTC
        CitaRequest request = new CitaRequest();
        request.setOrganizacionId(organizacionId);
        request.setPacienteId(pacienteId);
        request.setMedicoId(medicoId);
        request.setConsultorioId(consultorioId);
        request.setFechaHora(fechaCita);
        request.setDuracionMin((short) 30);
        request.setMotivo("Consulta general");

        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Paciente paciente = new Paciente();
        paciente.setId(pacienteId);
        Medico medico = new Medico();
        medico.setId(medicoId);
        medico.setOrganizacion(org);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(consultorioId);

        Cita citaGuardada = new Cita();
        citaGuardada.setId(citaId);
        citaGuardada.setOrganizacion(org);
        citaGuardada.setPaciente(paciente);
        citaGuardada.setMedico(medico);
        citaGuardada.setConsultorio(consultorio);
        citaGuardada.setFechaHora(request.getFechaHora());
        citaGuardada.setDuracionMin(request.getDuracionMin());
        citaGuardada.setMotivo(request.getMotivo());

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        when(citaRepository.save(any(Cita.class))).thenReturn(citaGuardada);

        java.time.LocalTime horaUtc = fechaCita.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalTime();
        int jsDay = fechaCita.withOffsetSameInstant(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() % 7;
        HorarioMedico horario = new HorarioMedico();
        horario.setHoraInicio(horaUtc.minusMinutes(30));
        horario.setHoraFin(horaUtc.plusMinutes(60));
        horario.setDuracionConsulta((short) 30);
        when(horarioMedicoRepository.findByMedicoIdAndDiaSemana(eq(medicoId), eq((short) jsDay)))
                .thenReturn(List.of(horario));
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(medicoId), any(), any()))
                .thenReturn(List.of());

        // Act
        CitaResponse response = citaService.crear(request);

        // Assert
        assertNotNull(response);
        assertEquals(citaId, response.getId());
        assertEquals(pacienteId, response.getPacienteId());
        assertEquals(medicoId, response.getMedicoId());
        verify(citaRepository, times(1)).save(any(Cita.class));
    }

    @Test
    public void testCrearCita_OrganizacionNotFound() {
        // Arrange
        CitaRequest request = new CitaRequest();
        request.setOrganizacionId(organizacionId);
        request.setPacienteId(pacienteId);
        request.setMedicoId(medicoId);
        request.setConsultorioId(consultorioId);
        request.setFechaHora(OffsetDateTime.now().plusHours(1));
        request.setDuracionMin((short) 30);
        request.setMotivo("Consulta general");

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> citaService.crear(request));
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    public void testBuscarPorId_Success() {
        // Arrange
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Paciente paciente = new Paciente();
        paciente.setId(pacienteId);
        Medico medico = new Medico();
        medico.setId(medicoId);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(consultorioId);

        Cita cita = new Cita();
        cita.setId(citaId);
        cita.setOrganizacion(org);
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setConsultorio(consultorio);
        cita.setFechaHora(OffsetDateTime.now().plusHours(1));
        cita.setDuracionMin((short) 30);
        cita.setMotivo("Consulta general");

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);

        // Act
        CitaResponse response = citaService.buscarPorId(citaId);

        // Assert
        assertNotNull(response);
        assertEquals(citaId, response.getId());
        assertEquals(medicoId, response.getMedicoId());
        verify(citaRepository, times(1)).findById(citaId);
    }

    @Test
    public void testBuscarPorId_NotFound() {
        // Arrange
        when(citaRepository.findById(citaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> citaService.buscarPorId(citaId));
    }

    @Test
    public void testListarPorOrganizacion_Success() {
        // Arrange
        Organizacion org1 = new Organizacion();
        org1.setId(organizacionId);
        Organizacion org2 = new Organizacion();
        org2.setId(organizacionId);

        Paciente paciente1 = new Paciente();
        paciente1.setId(pacienteId);
        Paciente paciente2 = new Paciente();
        paciente2.setId(UUID.randomUUID());

        Medico medico1 = new Medico();
        medico1.setId(medicoId);
        Medico medico2 = new Medico();
        medico2.setId(UUID.randomUUID());

        Consultorio consultorio1 = new Consultorio();
        consultorio1.setId(consultorioId);
        Consultorio consultorio2 = new Consultorio();
        consultorio2.setId(UUID.randomUUID());

        Cita cita1 = new Cita();
        cita1.setId(UUID.randomUUID());
        cita1.setOrganizacion(org1);
        cita1.setPaciente(paciente1);
        cita1.setMedico(medico1);
        cita1.setConsultorio(consultorio1);
        cita1.setFechaHora(OffsetDateTime.now().plusHours(1));

        Cita cita2 = new Cita();
        cita2.setId(UUID.randomUUID());
        cita2.setOrganizacion(org2);
        cita2.setPaciente(paciente2);
        cita2.setMedico(medico2);
        cita2.setConsultorio(consultorio2);
        cita2.setFechaHora(OffsetDateTime.now().plusHours(2));

        List<Cita> citas = List.of(cita1, cita2);

        when(citaRepository.findByOrganizacionId(organizacionId)).thenReturn(citas);

        // Act
        List<CitaResponse> responses = citaService.listarPorOrganizacion(organizacionId);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(citaRepository, times(1)).findByOrganizacionId(organizacionId);
    }

    // ── Tests de wiring de AuditClient ──

    @Test
    public void testCrear_auditaConAccionCorrecta() {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        OffsetDateTime fechaCita = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0); // fecha fija: evita fallos cuando "ahora + N h" cruza medianoche UTC
        CitaRequest request = citaRequestBase(fechaCita);

        Organizacion org = org();
        Paciente paciente = paciente();
        Medico medico = medico(org);
        Consultorio consultorio = consultorio();
        Cita saved = citaGuardada(org, paciente, medico, consultorio, fechaCita);

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        when(citaRepository.save(any(Cita.class))).thenReturn(saved);
        configurarHorario(medicoId, fechaCita);

        citaService.crear(request);

        verify(auditClient).audit(eq("CITA_CREAR"), eq("CITA"), eq(citaId), eq(organizacionId), isNull(), anyMap());
    }

    @Test
    public void testActualizar_auditaConAccionCorrecta() {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        OffsetDateTime fechaCita = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0); // fecha fija: evita fallos cuando "ahora + N h" cruza medianoche UTC
        CitaRequest request = new CitaRequest();
        request.setFechaHora(fechaCita);
        request.setDuracionMin((short) 30);

        Organizacion org = org();
        Cita existente = new Cita();
        existente.setId(citaId);
        existente.setOrganizacion(org);
        existente.setPaciente(paciente());
        existente.setMedico(medico(org));
        existente.setConsultorio(consultorio());
        existente.setEstado("SIN_CONFIRMAR");

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(existente));
        when(citaRepository.save(any())).thenReturn(existente);
        configurarHorario(existente.getMedico().getId(), fechaCita);

        citaService.actualizar(citaId, request);

        verify(auditClient).audit(eq("CITA_ACTUALIZAR"), eq("CITA"), any(), eq(organizacionId), isNull(), anyMap());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCambiarEstado_auditaConMetadataEstados() {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        Organizacion org = org();
        Cita existente = new Cita();
        existente.setId(citaId);
        existente.setOrganizacion(org);
        existente.setPaciente(paciente());
        existente.setMedico(medico(org));
        existente.setConsultorio(consultorio());
        existente.setEstado("SIN_CONFIRMAR");

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(existente));
        when(citaRepository.save(any())).thenReturn(existente);

        citaService.cambiarEstado(citaId, "CONFIRMADA");

        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditClient).audit(eq("CITA_CAMBIAR_ESTADO"), eq("CITA"), any(), eq(organizacionId), isNull(), metaCaptor.capture());
        Map<String, Object> meta = metaCaptor.getValue();
        assertEquals("SIN_CONFIRMAR", meta.get("estadoAnterior"));
        assertEquals("CONFIRMADA", meta.get("estadoNuevo"));
    }

    @Test
    public void testEliminar_auditaConAccionCorrecta() {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        Organizacion org = org();
        Cita existente = new Cita();
        existente.setId(citaId);
        existente.setOrganizacion(org);

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(existente));

        citaService.eliminar(citaId);

        verify(citaRepository).deleteById(citaId);
        verify(auditClient).audit(eq("CITA_ELIMINAR"), eq("CITA"), eq(citaId), eq(organizacionId), isNull(), anyMap());
    }

    @Test
    public void testCrear_auditLanzaExcepcion_operacionNoFalla() {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        OffsetDateTime fechaCita = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0); // fecha fija: evita fallos cuando "ahora + N h" cruza medianoche UTC
        CitaRequest request = citaRequestBase(fechaCita);

        Organizacion org = org();
        Paciente paciente = paciente();
        Medico medico = medico(org);
        Consultorio consultorio = consultorio();
        Cita saved = citaGuardada(org, paciente, medico, consultorio, fechaCita);

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        when(citaRepository.save(any(Cita.class))).thenReturn(saved);
        configurarHorario(medicoId, fechaCita);
        doThrow(new RuntimeException("audit timeout")).when(auditClient)
                .audit(anyString(), anyString(), any(), any(), any(), anyMap());

        assertDoesNotThrow(() -> citaService.crear(request));
        verify(citaRepository).save(any(Cita.class));
    }

    // ── Helpers para los tests de auditoría ──

    private Organizacion org() {
        Organizacion o = new Organizacion();
        o.setId(organizacionId);
        return o;
    }

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setId(pacienteId);
        return p;
    }

    private Medico medico(Organizacion org) {
        Medico m = new Medico();
        m.setId(medicoId);
        m.setOrganizacion(org);
        return m;
    }

    private Consultorio consultorio() {
        Consultorio c = new Consultorio();
        c.setId(consultorioId);
        return c;
    }

    private Cita citaGuardada(Organizacion org, Paciente paciente, Medico medico, Consultorio consultorio, OffsetDateTime fecha) {
        Cita c = new Cita();
        c.setId(citaId);
        c.setOrganizacion(org);
        c.setPaciente(paciente);
        c.setMedico(medico);
        c.setConsultorio(consultorio);
        c.setFechaHora(fecha);
        c.setDuracionMin((short) 30);
        return c;
    }

    private CitaRequest citaRequestBase(OffsetDateTime fecha) {
        CitaRequest req = new CitaRequest();
        req.setOrganizacionId(organizacionId);
        req.setPacienteId(pacienteId);
        req.setMedicoId(medicoId);
        req.setConsultorioId(consultorioId);
        req.setFechaHora(fecha);
        req.setDuracionMin((short) 30);
        return req;
    }

    private void configurarHorario(UUID mId, OffsetDateTime fecha) {
        java.time.LocalTime horaUtc = fecha.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalTime();
        int jsDay = fecha.withOffsetSameInstant(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() % 7;
        HorarioMedico horario = new HorarioMedico();
        horario.setHoraInicio(horaUtc.minusMinutes(30));
        horario.setHoraFin(horaUtc.plusMinutes(60));
        horario.setDuracionConsulta((short) 30);
        when(horarioMedicoRepository.findByMedicoIdAndDiaSemana(eq(mId), eq((short) jsDay)))
                .thenReturn(List.of(horario));
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(mId), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    public void testActualizarCita_Success() {
        // Arrange
        OffsetDateTime fechaCita = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0); // fecha fija: evita fallos cuando "ahora + N h" cruza medianoche UTC
        CitaRequest request = new CitaRequest();
        request.setFechaHora(fechaCita);
        request.setDuracionMin((short) 45);
        request.setMotivo("Consulta actualizada");

        Cita citaExistente = new Cita();
        citaExistente.setId(citaId);
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Paciente paciente = new Paciente();
        paciente.setId(pacienteId);
        Medico medico = new Medico();
        medico.setId(medicoId);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(consultorioId);

        citaExistente.setOrganizacion(org);
        citaExistente.setPaciente(paciente);
        citaExistente.setMedico(medico);
        citaExistente.setConsultorio(consultorio);

        java.time.LocalTime horaUtc = fechaCita.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalTime();
        int jsDay = fechaCita.withOffsetSameInstant(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() % 7;
        HorarioMedico horario = new HorarioMedico();
        horario.setHoraInicio(horaUtc.minusMinutes(30));
        horario.setHoraFin(horaUtc.plusMinutes(60));
        horario.setDuracionConsulta((short) 30);
        when(horarioMedicoRepository.findByMedicoIdAndDiaSemana(eq(medicoId), eq((short) jsDay)))
                .thenReturn(List.of(horario));
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(medicoId), any(), any()))
                .thenReturn(List.of());

        when(citaRepository.findById(citaId)).thenReturn(Optional.of(citaExistente));
        when(citaRepository.save(any(Cita.class))).thenReturn(citaExistente);

        // Act
        CitaResponse response = citaService.actualizar(citaId, request);

        // Assert
        assertNotNull(response);
        verify(citaRepository, times(1)).save(any(Cita.class));
    }

    // ── Zona horaria: "ahora" = hora de reloj de la clínica expresada en UTC ──────────

    /** 16:00Z reales = 10:00 en America/Mexico_City (CST, UTC-6) → ahora de la clínica = 10:00Z. */
    private static final java.time.Instant INSTANTE_10AM_CDMX = java.time.Instant.parse("2026-11-10T16:00:00Z");

    private CitaService citaServiceConRelojFijo() {
        ClinicaReloj fijo = new ClinicaReloj(java.time.Clock.fixed(INSTANTE_10AM_CDMX, java.time.ZoneOffset.UTC));
        return new CitaService(citaRepository, organizacionRepository, pacienteRepository, medicoRepository,
                consultorioRepository, horarioMedicoRepository, auditClient, fijo);
    }

    private CitaRequest prepararCreacion(OffsetDateTime fechaCita) {
        autenticarComo("ADMIN", UUID.randomUUID(), organizacionId);
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Paciente paciente = new Paciente();
        paciente.setId(pacienteId);
        Medico medico = new Medico();
        medico.setId(medicoId);
        medico.setOrganizacion(org);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(consultorioId);

        Cita guardada = new Cita();
        guardada.setId(citaId);
        guardada.setOrganizacion(org);
        guardada.setPaciente(paciente);
        guardada.setMedico(medico);
        guardada.setConsultorio(consultorio);
        guardada.setFechaHora(fechaCita);
        guardada.setDuracionMin((short) 30);

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        when(citaRepository.save(any(Cita.class))).thenReturn(guardada);

        HorarioMedico horario = new HorarioMedico();
        horario.setHoraInicio(java.time.LocalTime.of(8, 0));
        horario.setHoraFin(java.time.LocalTime.of(14, 0));
        horario.setDuracionConsulta((short) 30);
        int jsDay = fechaCita.getDayOfWeek().getValue() % 7;
        when(horarioMedicoRepository.findByMedicoIdAndDiaSemana(eq(medicoId), eq((short) jsDay)))
                .thenReturn(List.of(horario));
        when(citaRepository.findByMedicoIdAndFechaHoraBetween(eq(medicoId), any(), any()))
                .thenReturn(List.of());

        CitaRequest request = new CitaRequest();
        request.setOrganizacionId(organizacionId);
        request.setPacienteId(pacienteId);
        request.setMedicoId(medicoId);
        request.setConsultorioId(consultorioId);
        request.setFechaHora(fechaCita);
        request.setDuracionMin((short) 30);
        request.setMotivo("Consulta general");
        return request;
    }

    @Test
    public void testCrear_horaDeRelojFuturaEnClinica_seAceptaAunqueYaPasoEnUtcReal() {
        // 11:00Z = 11:00 en la clínica; en UTC real ya son las 16:00, pero la cita es futura.
        CitaRequest request = prepararCreacion(OffsetDateTime.parse("2026-11-10T11:00:00Z"));

        CitaResponse response = assertDoesNotThrow(() -> citaServiceConRelojFijo().crear(request));

        assertEquals(citaId, response.getId());
        verify(citaRepository).save(any(Cita.class));
    }

    @Test
    public void testCrear_horaDeRelojPasadaEnClinica_seRechaza() {
        // 09:00Z = 09:00 en la clínica, antes de las 10:00 actuales.
        CitaRequest request = prepararCreacion(OffsetDateTime.parse("2026-11-10T09:00:00Z"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaServiceConRelojFijo().crear(request));

        assertEquals("No se puede agendar una cita en fecha y hora pasada", ex.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    public void testListarHorasDisponibles_ocultaSoloBloquesAnterioresALaHoraDeLaClinica() {
        prepararCreacion(OffsetDateTime.parse("2026-11-10T11:00:00Z"));

        List<String> horas = citaServiceConRelojFijo()
                .listarHorasDisponibles(medicoId, java.time.LocalDate.of(2026, 11, 10), (short) 30);

        // 10:00 en punto ya no es "después de ahora"; 10:30 en adelante sí.
        assertEquals(List.of("10:30", "11:00", "11:30", "12:00", "12:30", "13:00", "13:30"), horas);
    }
}
