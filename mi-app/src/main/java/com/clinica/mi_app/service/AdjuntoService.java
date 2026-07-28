package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.request.AdjuntoRequest;
import com.clinica.mi_app.dto.request.SolicitudEstudioRequest;
import com.clinica.mi_app.dto.response.AdjuntoResponse;
import org.springframework.web.multipart.MultipartFile;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.mapper.AdjuntoMapper;
import com.clinica.mi_app.model.*;
import com.clinica.mi_app.repository.*;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdjuntoService {

    private final AdjuntoRepository repo;
    private final OrganizacionRepository orgRepo;
    private final PacienteRepository pacienteRepo;
    private final CitaRepository citaRepo;
    private final UsuarioRepository usuarioRepo;
    private final NotificacionRepository notificacionRepo;
    private final FileStorageService fileStorageService;

    public AdjuntoService(AdjuntoRepository repo, OrganizacionRepository orgRepo,
                          PacienteRepository pacienteRepo, CitaRepository citaRepo,
                          UsuarioRepository usuarioRepo, NotificacionRepository notificacionRepo,
                          FileStorageService fileStorageService) {
        this.repo = repo;
        this.orgRepo = orgRepo;
        this.pacienteRepo = pacienteRepo;
        this.citaRepo = citaRepo;
        this.usuarioRepo = usuarioRepo;
        this.notificacionRepo = notificacionRepo;
        this.fileStorageService = fileStorageService;
    }

    // ── Listados existentes ──────────────────────────────────────────────────

    public List<AdjuntoResponse> listarPorPaciente(UUID pacienteId) {
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            Paciente paciente = pacienteRepo.findById(pacienteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Adjunto", pacienteId.toString()));
            if (!AuthenticatedUser.getEmail().equals(paciente.getEmail())) {
                throw new ResourceNotFoundException("Adjunto", pacienteId.toString());
            }
        }
        return repo.findByPacienteId(pacienteId).stream().map(AdjuntoMapper::toResponse).collect(Collectors.toList());
    }

    public List<AdjuntoResponse> listarPorCita(UUID citaId) {
        return repo.findByCitaId(citaId).stream().map(AdjuntoMapper::toResponse).collect(Collectors.toList());
    }

    public List<AdjuntoResponse> listarPendientesDeNotificar(UUID organizacionId) {
        return repo.findByOrganizacionIdAndNotificarTrue(organizacionId).stream().map(AdjuntoMapper::toResponse).collect(Collectors.toList());
    }

    public AdjuntoResponse buscarPorId(UUID id) {
        Adjunto a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", id.toString()));
        if (Roles.PACIENTE.equals(AuthenticatedUser.getRol())) {
            if (!AuthenticatedUser.getEmail().equals(a.getPaciente().getEmail())) {
                throw new ResourceNotFoundException("Adjunto", id.toString());
            }
        }
        return AdjuntoMapper.toResponse(a);
    }

    // ── Mis estudios (paciente autenticado) ─────────────────────────────────

    public List<AdjuntoResponse> listarMisEstudios() {
        String email = AuthenticatedUser.getEmail();
        UUID orgId = AuthenticatedUser.getOrganizacionId();
        Paciente paciente = pacienteRepo.findByOrganizacionIdAndEmail(orgId, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
        return repo.findByPacienteIdOrderByCreatedAtDesc(paciente.getId())
                .stream().map(AdjuntoMapper::toResponse).collect(Collectors.toList());
    }

    // ── Workflow estudios ────────────────────────────────────────────────────

    /**
     * Médico/Admin solicita un estudio al paciente.
     * Crea el adjunto en estado SOLICITADO, sin archivo adjunto aún.
     */
    public AdjuntoResponse solicitar(SolicitudEstudioRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Paciente paciente = pacienteRepo.findById(req.getPacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", req.getPacienteId().toString()));
        Cita cita = citaRepo.findById(req.getCitaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita", req.getCitaId().toString()));
        Usuario solicitadoPor = usuarioRepo.findById(req.getSubidoPorId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", req.getSubidoPorId().toString()));

        Adjunto a = new Adjunto();
        a.setOrganizacion(org);
        a.setPaciente(paciente);
        a.setCita(cita);
        a.setSubidoPor(solicitadoPor);
        a.setTipo(req.getTipo());
        a.setEstado("SOLICITADO");
        a.setNotificar(false);

        return AdjuntoMapper.toResponse(repo.save(a));
    }

    /**
     * Paciente sube el archivo directamente desde su dispositivo.
     * El archivo se valida (extensión + MIME), se almacena en Supabase Storage
     * y el adjunto pasa a estado SUBIDO. Se genera notificación para el médico.
     */
    public AdjuntoResponse uploadArchivo(UUID id, MultipartFile archivo) {
        Adjunto a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", id.toString()));

        // Solo el paciente dueño puede subir el resultado
        if (!AuthenticatedUser.getEmail().equals(a.getPaciente().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para cargar este estudio");
        }
        if (!"SOLICITADO".equals(a.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden cargar archivos en estudios con estado SOLICITADO");
        }

        // Subir a Supabase Storage (valida extensión + MIME internamente)
        FileStorageService.FileUploadResult resultado = fileStorageService.subir(archivo);

        a.setNombreArchivo(resultado.nombreArchivo());
        a.setUrlArchivo(resultado.urlArchivo());
        a.setMimeType(resultado.mimeType());
        a.setEstado("SUBIDO");

        Adjunto saved = repo.save(a);

        // Notificar al médico
        if (saved.getCita() != null) {
            Notificacion notif = new Notificacion();
            notif.setOrganizacion(saved.getOrganizacion());
            notif.setCita(saved.getCita());
            notif.setAdjunto(saved);
            notif.setTipo("ADJUNTO");
            notif.setCanal("WHATSAPP");
            notificacionRepo.save(notif);
        }

        return AdjuntoMapper.toResponse(saved);
    }

    /**
     * Paciente sube la URL del resultado del estudio.
     * Pasa el estado a SUBIDO y crea una notificación para el médico.
     * @deprecated Usar {@link #uploadArchivo(UUID, MultipartFile)} para carga directa de archivos.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public AdjuntoResponse cargarArchivo(UUID id, String url, String nombre, String mimeType) {
        Adjunto a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", id.toString()));

        // Seguridad: sólo el paciente dueño puede cargar
        if (!AuthenticatedUser.getEmail().equals(a.getPaciente().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para cargar este estudio");
        }
        if (!"SOLICITADO".equals(a.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden cargar estudios en estado SOLICITADO");
        }

        a.setUrlArchivo(url);
        a.setNombreArchivo(nombre);
        a.setMimeType(mimeType);
        a.setEstado("SUBIDO");

        Adjunto saved = repo.save(a);

        // Crear notificación para que el médico sepa que el paciente subió el resultado
        if (saved.getCita() != null) {
            Notificacion notif = new Notificacion();
            notif.setOrganizacion(saved.getOrganizacion());
            notif.setCita(saved.getCita());
            notif.setAdjunto(saved);
            notif.setTipo("ADJUNTO");
            notif.setCanal("WHATSAPP");
            notificacionRepo.save(notif);
        }

        return AdjuntoMapper.toResponse(saved);
    }

    /**
     * Médico/Admin revisa el resultado del estudio y deja un comentario.
     * Pasa el estado a REVISADO.
     */
    public AdjuntoResponse revisar(UUID id, String comentario, String nuevoEstado) {
        Adjunto a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", id.toString()));

        if (!"SUBIDO".equals(a.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden revisar estudios en estado SUBIDO");
        }
        if (!"REVISADO".equals(nuevoEstado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no válido. Use: REVISADO");
        }

        a.setEstado(nuevoEstado);
        a.setComentarioRevision(comentario);
        a.setRevisadoEn(OffsetDateTime.now());

        return AdjuntoMapper.toResponse(repo.save(a));
    }

    // ── CRUD existente ───────────────────────────────────────────────────────

    public AdjuntoResponse crear(AdjuntoRequest req) {
        Organizacion org = orgRepo.findById(req.getOrganizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion", req.getOrganizacionId().toString()));
        Paciente paciente = pacienteRepo.findById(req.getPacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", req.getPacienteId().toString()));
        Usuario subidoPor = usuarioRepo.findById(req.getSubidoPorId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", req.getSubidoPorId().toString()));
        Adjunto a = new Adjunto();
        a.setOrganizacion(org);
        a.setPaciente(paciente);
        a.setSubidoPor(subidoPor);
        a.setTipo(req.getTipo());
        a.setNombreArchivo(req.getNombreArchivo());
        a.setUrlArchivo(req.getUrlArchivo());
        a.setMimeType(req.getMimeType());
        a.setNotificar(req.getNotificar() != null ? req.getNotificar() : false);
        if (req.getCitaId() != null) {
            Cita cita = citaRepo.findById(req.getCitaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cita", req.getCitaId().toString()));
            a.setCita(cita);
        }
        return AdjuntoMapper.toResponse(repo.save(a));
    }

    public AdjuntoResponse actualizar(UUID id, AdjuntoRequest req) {
        Adjunto a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto", id.toString()));
        a.setTipo(req.getTipo());
        a.setNombreArchivo(req.getNombreArchivo());
        a.setUrlArchivo(req.getUrlArchivo());
        a.setMimeType(req.getMimeType());
        a.setNotificar(req.getNotificar() != null ? req.getNotificar() : false);
        return AdjuntoMapper.toResponse(repo.save(a));
    }

    public void eliminar(UUID id) {
        repo.deleteById(id);
    }
}
