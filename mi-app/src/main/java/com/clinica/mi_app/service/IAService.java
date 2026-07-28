package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.CitaRequest;
import com.clinica.mi_app.dto.response.CitaResponse;
import com.clinica.mi_app.dto.response.MedicoPublicoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.HorarioMedico;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Paciente;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.HorarioMedicoRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PacienteRepository;
import com.clinica.mi_app.security.JwtUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IAService {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private final WebClient fastapiWebClient;
    private final JwtUtil jwtUtil;
    private final PacienteRepository pacienteRepository;
    private final CitaService citaService;
    private final CitaRepository citaRepository;
    private final OrganizacionRepository organizacionRepository;
    private final MedicoService medicoService;
    private final HorarioMedicoRepository horarioMedicoRepository;

    public IAService(WebClient fastapiWebClient,
                     JwtUtil jwtUtil,
                     PacienteRepository pacienteRepository,
                     CitaService citaService,
                     CitaRepository citaRepository,
                     OrganizacionRepository organizacionRepository,
                     MedicoService medicoService,
                     HorarioMedicoRepository horarioMedicoRepository) {
        this.fastapiWebClient = fastapiWebClient;
        this.jwtUtil = jwtUtil;
        this.pacienteRepository = pacienteRepository;
        this.citaService = citaService;
        this.citaRepository = citaRepository;
        this.organizacionRepository = organizacionRepository;
        this.medicoService = medicoService;
        this.horarioMedicoRepository = horarioMedicoRepository;
    }

    // ── Disponibilidad ──────────────────────────────────────────────────────────

    @CircuitBreaker(name = "fastapi-ia", fallbackMethod = "fallbackDisponibilidad")
    public String consultarDisponibilidad(String orgId, String authHeader) {
        return fastapiWebClient.get()
                .uri("/api/ia/disponibilidad/{orgId}", orgId)
                .headers(h -> { if (authHeader != null) h.set("Authorization", authHeader); })
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String fallbackDisponibilidad(String orgId, String authHeader, Exception ex) {
        return "{\"mensaje\": \"Servicio de IA no disponible. Contacte directamente a la clínica.\"}";
    }

    // ── Chat ────────────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "fastapi-ia", fallbackMethod = "fallbackChat")
    public String enviarMensajeChat(String mensaje, List<Map<String, String>> historial, String authHeader) {
        Map<String, Object> body = new HashMap<>();
        body.put("mensaje", mensaje);
        body.put("historial", historial != null ? historial : List.of());
        try {
            String token = authHeader != null ? authHeader.replace("Bearer ", "") : "";
            Claims claims = jwtUtil.validateToken(token);
            body.put("organizacion_id", claims.get("organizacionId", String.class));
        } catch (Exception ignored) {}

        return fastapiWebClient.post()
                .uri("/api/ia/chat")
                .headers(h -> { if (authHeader != null) h.set("Authorization", authHeader); })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String fallbackChat(String mensaje, List<Map<String, String>> historial, String authHeader, Exception ex) {
        String[] opciones = {
            "Hola, disculpa la interrupción. En este momento estoy atendiendo varias solicitudes a la vez. Por favor intenta nuevamente en unos segundos, con gusto te ayudo.",
            "Hola, disculpa. En este momento tengo un pequeño problema de conexión. Por favor intenta nuevamente en un momento, estaré disponible para ayudarte.",
            "Hola, perdona el inconveniente. Parece que estoy experimentando una falla temporal. Intenta de nuevo en unos instantes, no tardará.",
            "Hola, disculpa la demora. Estoy procesando varias solicitudes al mismo tiempo. Gracias por tu paciencia, en un momento estaré contigo."
        };
        String msg = opciones[(int)(Math.random() * opciones.length)];
        return "{\"respuesta\": \"" + msg + "\"}";
    }

    // ── Médicos para IA (DTO público, sin datos sensibles) ──────────────────────

    public List<MedicoPublicoResponse> getMedicosPublicos(UUID orgId) {
        return medicoService.listarPublicosPorOrganizacion(orgId);
    }

    // ── Horarios disponibles para IA ────────────────────────────────────────────

    /**
     * Calcula los slots libres de un médico para una fecha dada.
     * Toma los bloques de HorarioMedico y resta las citas ya existentes.
     * diaSemana en HorarioMedico: 0=lunes … 6=domingo (DayOfWeek.getValue()-1).
     */
    public List<String> getSlotsDisponibles(UUID medicoId, LocalDate fecha) {
        short diaSemana = (short) (fecha.getDayOfWeek().getValue() - 1);
        List<HorarioMedico> bloques = horarioMedicoRepository.findByMedicoIdAndDiaSemana(medicoId, diaSemana);
        if (bloques.isEmpty()) return List.of();

        OffsetDateTime inicioDia = fecha.atStartOfDay(ZONA).toOffsetDateTime();
        OffsetDateTime finDia    = fecha.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();

        Set<LocalTime> ocupadas = citaRepository
                .findByMedicoIdAndFechaHoraBetween(medicoId, inicioDia, finDia)
                .stream()
                .map(c -> c.getFechaHora().atZoneSameInstant(ZONA).toLocalTime())
                .collect(Collectors.toSet());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<String> slots = new ArrayList<>();
        for (HorarioMedico bloque : bloques) {
            LocalTime hora = bloque.getHoraInicio();
            while (!hora.plusMinutes(bloque.getDuracionConsulta()).isAfter(bloque.getHoraFin())) {
                if (!ocupadas.contains(hora)) slots.add(hora.format(fmt));
                hora = hora.plusMinutes(bloque.getDuracionConsulta());
            }
        }
        return slots;
    }

    // ── Agendado desde IA ───────────────────────────────────────────────────────

    public CitaResponse agendarCitaDesdeIA(Map<String, Object> body, String authHeader) {
        String token = authHeader != null ? authHeader.replace("Bearer ", "") : "";
        Claims claims = jwtUtil.validateToken(token);
        String email = claims.get("email", String.class);
        UUID organizacionId = UUID.fromString(claims.get("organizacionId", String.class));

        Paciente paciente = pacienteRepository
                .findByOrganizacionIdAndEmail(organizacionId, email)
                .orElseGet(() -> {
                    Organizacion org = organizacionRepository.findById(organizacionId)
                            .orElseThrow(() -> new ResourceNotFoundException("Organizacion", organizacionId.toString()));
                    Paciente nuevo = new Paciente();
                    nuevo.setEmail(email);
                    nuevo.setNombre(email.contains("@") ? email.split("@")[0] : email);
                    nuevo.setOrganizacion(org);
                    nuevo.setActivo(true);
                    return pacienteRepository.save(nuevo);
                });

        CitaRequest req = new CitaRequest();
        req.setOrganizacionId(organizacionId);
        req.setPacienteId(paciente.getId());
        req.setMedicoId(UUID.fromString((String) body.get("medico_id")));
        req.setConsultorioId(UUID.fromString((String) body.get("consultorio_id")));
        req.setFechaHora(parseFechaHora((String) body.get("fecha_hora")));
        Object dur = body.getOrDefault("duracion_min", 30);
        req.setDuracionMin(dur instanceof Number ? ((Number) dur).shortValue() : Short.parseShort(dur.toString()));
        req.setMotivo(body.getOrDefault("motivo", "Cita agendada por asistente virtual").toString());

        return citaService.crear(req);
    }

    /** Parsea ISO 8601 con o sin timezone; si falta, asume America/Mexico_City. */
    private OffsetDateTime parseFechaHora(String fechaHoraStr) {
        try {
            return OffsetDateTime.parse(fechaHoraStr);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(fechaHoraStr).atZone(ZONA).toOffsetDateTime();
        }
    }
}
