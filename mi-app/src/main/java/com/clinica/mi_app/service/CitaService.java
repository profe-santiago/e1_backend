package com.clinica.mi_app.service;

import com.clinica.mi_app.client.AuditClient;
import com.clinica.mi_app.dto.request.CitaRequest;
import com.clinica.mi_app.dto.response.CitaResponse;
import com.clinica.mi_app.dto.response.DisponibilidadSlot;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.CitaMapper;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.HorarioMedico;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.ConsultorioRepository;
import com.clinica.mi_app.repository.HorarioMedicoRepository;
import com.clinica.mi_app.repository.MedicoRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PacienteRepository;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CitaService {

    private static final Logger log = LoggerFactory.getLogger(CitaService.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "SIN_CONFIRMAR", "CONFIRMADA", "CANCELADA", "REAGENDADA", "NO_ASISTIO"
    );

    private final CitaRepository repo;
    private final OrganizacionRepository orgRepo;
    private final PacienteRepository pacienteRepo;
    private final MedicoRepository medicoRepo;
    private final ConsultorioRepository consultorioRepo;
    private final HorarioMedicoRepository horarioRepo;
    private final AuditClient auditClient;

    public CitaService(CitaRepository repo, OrganizacionRepository orgRepo,
                       PacienteRepository pacienteRepo, MedicoRepository medicoRepo,
                       ConsultorioRepository consultorioRepo, HorarioMedicoRepository horarioRepo,
                       AuditClient auditClient) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.pacienteRepo = pacienteRepo;
        this.medicoRepo = medicoRepo;
        this.consultorioRepo = consultorioRepo;
        this.horarioRepo = horarioRepo;
        this.auditClient = auditClient;
    }

    public List<CitaResponse> listarPorOrganizacion(UUID organizacionId) {
        return repo.findByOrganizacionId(organizacionId).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarPorMedico(UUID medicoId) {
        return repo.findByMedicoId(medicoId).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarMisCitas() {
        String email = AuthenticatedUser.getEmail();
        UUID orgId = AuthenticatedUser.getOrganizacionId();
        Paciente paciente = pacienteRepo.findByOrganizacionIdAndEmail(orgId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", email));
        return repo.findByPacienteId(paciente.getId()).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarPorPaciente(UUID pacienteId) {
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            Paciente paciente = pacienteRepo.findById(pacienteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cita", pacienteId.toString()));
            if (!AuthenticatedUser.getEmail().equals(paciente.getEmail())) {
                throw new ResourceNotFoundException("Cita", pacienteId.toString());
            }
        }
        return repo.findByPacienteId(pacienteId).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarPorEstado(UUID organizacionId, String estado) {
        return repo.findByOrganizacionIdAndEstado(organizacionId, estado).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarPorPacienteYEstado(UUID pacienteId, String estado) {
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            Paciente paciente = pacienteRepo.findById(pacienteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cita", pacienteId.toString()));
            if (!AuthenticatedUser.getEmail().equals(paciente.getEmail())) {
                throw new ResourceNotFoundException("Cita", pacienteId.toString());
            }
        }
        return repo.findByPacienteIdAndEstado(pacienteId, estado).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<CitaResponse> listarPorMedicoEnRango(UUID medicoId, OffsetDateTime inicio, OffsetDateTime fin) {
        return repo.findByMedicoIdAndFechaHoraBetween(medicoId, inicio, fin).stream()
                .map(CitaMapper::toResponse).collect(Collectors.toList());
    }

    public List<String> listarHorasDisponibles(UUID medicoId, LocalDate fecha, Short duracionMin) {
        Medico medico = medicoRepo.findById(medicoId)
                .orElseThrow(() -> new ResourceNotFoundException("Medico", medicoId.toString()));
        short diaSemana = (short) (fecha.getDayOfWeek().getValue() % 7);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime inicioDia = fecha.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime finDia = fecha.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<Cita> citasDelDia = repo.findByMedicoIdAndFechaHoraBetween(medico.getId(), inicioDia, finDia);

        return horarioRepo.findByMedicoIdAndDiaSemana(medico.getId(), diaSemana)
                .stream()
                .flatMap(horario -> crearBloques(fecha, horario.getHoraInicio(), horario.getHoraFin(),
                        duracionMin != null ? duracionMin : horario.getDuracionConsulta(), citasDelDia, zone).stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public CitaResponse buscarPorId(UUID id) {
        Cita cita = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita", id.toString()));
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())
                && !AuthenticatedUser.getEmail().equals(cita.getPaciente().getEmail())) {
            throw new ResourceNotFoundException("Cita", id.toString());
        }
        return CitaMapper.toResponse(cita);
    }

    @Transactional(readOnly = true)
    public List<DisponibilidadSlot> calcularDisponibilidad(UUID medicoId, LocalDate fecha, UUID excludeId) {
        int jsDay = fecha.getDayOfWeek().getValue() % 7;
        List<HorarioMedico> horarios = horarioRepo.findByMedicoIdAndDiaSemana(medicoId, (short) jsDay);
        if (horarios.isEmpty()) return List.of();

        List<DisponibilidadSlot> todos = new ArrayList<>();
        for (HorarioMedico h : horarios) {
            LocalTime current = h.getHoraInicio();
            while (!current.plusMinutes(h.getDuracionConsulta()).isAfter(h.getHoraFin())) {
                LocalTime fin = current.plusMinutes(h.getDuracionConsulta());
                todos.add(new DisponibilidadSlot(
                        current.format(HM),
                        fin.format(HM),
                        h.getDuracionConsulta().intValue()
                ));
                current = fin;
            }
        }

        OffsetDateTime startOfDay = fecha.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endOfDay = fecha.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        Set<String> ocupados = repo.findByMedicoIdAndFechaHoraBetween(medicoId, startOfDay, endOfDay)
                .stream()
                .filter(c -> !"CANCELADA".equals(c.getEstado()))
                .filter(c -> excludeId == null || !excludeId.equals(c.getId()))
                .map(c -> c.getFechaHora().withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().format(HM))
                .collect(Collectors.toSet());

        return todos.stream()
                .filter(s -> !ocupados.contains(s.getHora()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CitaResponse crear(CitaRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Paciente paciente = pacienteRepo.findById(req.getPacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", req.getPacienteId().toString()));
        Medico medico = medicoRepo.findById(req.getMedicoId())
                .orElseThrow(() -> new ResourceNotFoundException("Medico", req.getMedicoId().toString()));

        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol()) && !AuthenticatedUser.getEmail().equals(paciente.getEmail())) {
            throw new ResourceNotFoundException("Paciente", req.getPacienteId().toString());
        }
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            org = paciente.getOrganizacion();
        }
        if (!medico.getOrganizacion().getId().equals(org.getId())) {
            throw new ResourceNotFoundException("Medico", req.getMedicoId().toString());
        }

        Consultorio consultorio = Roles.PACIENTE.equals(AuthenticatedUser.getRol())
                ? medico.getConsultorio()
                : consultorioRepo.findById(req.getConsultorioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Consultorio", req.getConsultorioId().toString()));

        validarDisponibilidad(req.getMedicoId(), req.getFechaHora(), null);

        Cita c = new Cita();
        c.setOrganizacion(org);
        c.setPaciente(paciente);
        c.setMedico(medico);
        c.setConsultorio(consultorio);
        c.setFechaHora(req.getFechaHora());
        c.setDuracionMin(req.getDuracionMin());
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            c.setEstado("SIN_CONFIRMAR");
        } else {
            aplicarEstado(c, req.getEstado());
        }
        c.setMotivo(req.getMotivo());
        Cita saved = repo.save(c);
        try {
            auditClient.audit("CITA_CREAR", "CITA", saved.getId(), org.getId(), null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit CITA_CREAR: {}", ex.getMessage());
        }
        return CitaMapper.toResponse(saved);
    }

    @Transactional
    public CitaResponse actualizar(UUID id, CitaRequest req) {
        Cita c = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita", id.toString()));

        validarDisponibilidad(c.getMedico().getId(), req.getFechaHora(), id);

        c.setFechaHora(req.getFechaHora());
        c.setDuracionMin(req.getDuracionMin());
        aplicarEstado(c, req.getEstado());
        c.setMotivo(req.getMotivo());
        Cita saved = repo.save(c);
        try {
            auditClient.audit("CITA_ACTUALIZAR", "CITA", saved.getId(), saved.getOrganizacion().getId(), null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit CITA_ACTUALIZAR: {}", ex.getMessage());
        }
        return CitaMapper.toResponse(saved);
    }

    @Transactional
    public void eliminar(UUID id) {
        Cita c = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita", id.toString()));
        UUID orgId = c.getOrganizacion().getId();
        repo.deleteById(id);
        try {
            auditClient.audit("CITA_ELIMINAR", "CITA", id, orgId, null, baseMeta());
        } catch (Exception ex) {
            log.warn("audit CITA_ELIMINAR: {}", ex.getMessage());
        }
    }

    @Transactional
    public CitaResponse cambiarEstado(UUID id, String nuevoEstado) {
        if (!ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new IllegalArgumentException("Estado no valido: " + nuevoEstado);
        }
        Cita c = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita", id.toString()));
        String estadoAnterior = c.getEstado();
        c.setEstado(nuevoEstado);
        Cita saved = repo.save(c);
        try {
            Map<String, Object> meta = baseMeta();
            meta.put("estadoAnterior", estadoAnterior);
            meta.put("estadoNuevo", nuevoEstado);
            auditClient.audit("CITA_CAMBIAR_ESTADO", "CITA", saved.getId(), saved.getOrganizacion().getId(), null, meta);
        } catch (Exception ex) {
            log.warn("audit CITA_CAMBIAR_ESTADO: {}", ex.getMessage());
        }
        return CitaMapper.toResponse(saved);
    }

    private Map<String, Object> baseMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", AuthenticatedUser.getUserId());
        m.put("rol", AuthenticatedUser.getRol());
        m.put("email", AuthenticatedUser.getEmail());
        return m;
    }

    private void validarDisponibilidad(UUID medicoId, OffsetDateTime fechaHora, UUID excludeId) {
        if (fechaHora.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("No se puede agendar una cita en fecha y hora pasada");
        }
        LocalDate fecha = fechaHora.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        String horaSlot = fechaHora.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().format(HM);
        List<DisponibilidadSlot> disponibles = calcularDisponibilidad(medicoId, fecha, excludeId);
        boolean libre = disponibles.stream().anyMatch(s -> s.getHora().equals(horaSlot));
        if (!libre) {
            throw new IllegalArgumentException("El horario seleccionado no esta disponible para este medico");
        }
    }

    private void aplicarEstado(Cita cita, String estado) {
        if (estado == null || estado.isBlank()) {
            return;
        }
        if (!ESTADOS_VALIDOS.contains(estado)) {
            throw new IllegalArgumentException("Estado de cita invalido: " + estado);
        }
        cita.setEstado(estado);
    }

    private List<String> crearBloques(LocalDate fecha, LocalTime inicio, LocalTime fin, Short duracionMin,
                                      List<Cita> citas, ZoneId zone) {
        List<String> bloques = new ArrayList<>();
        LocalTime actual = inicio;
        while (!actual.plusMinutes(duracionMin).isAfter(fin)) {
            OffsetDateTime bloqueInicio = fecha.atTime(actual).atZone(zone).toOffsetDateTime();
            OffsetDateTime bloqueFin = bloqueInicio.plusMinutes(duracionMin);
            boolean ocupado = citas.stream().anyMatch(cita -> {
                if (Set.of("CANCELADA", "NO_ASISTIO").contains(cita.getEstado())) {
                    return false;
                }
                OffsetDateTime citaInicio = cita.getFechaHora();
                OffsetDateTime citaFin = citaInicio.plusMinutes(cita.getDuracionMin());
                return bloqueInicio.isBefore(citaFin) && bloqueFin.isAfter(citaInicio);
            });
            if (!ocupado && bloqueInicio.isAfter(OffsetDateTime.now(zone))) {
                bloques.add(actual.toString());
            }
            actual = actual.plusMinutes(duracionMin);
        }
        return bloques;
    }
}
