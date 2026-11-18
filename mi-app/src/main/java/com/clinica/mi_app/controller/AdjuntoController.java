package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.AdjuntoRequest;
import com.clinica.mi_app.dto.request.SolicitudEstudioRequest;
import com.clinica.mi_app.dto.response.AdjuntoResponse;
import com.clinica.mi_app.service.AdjuntoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.clinica.mi_app.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/adjuntos")
@Tag(name = "Adjuntos", description = "Gestión de archivos y seguimiento de estudios médicos")
@SecurityRequirement(name = "bearerAuth")
public class AdjuntoController {

    private final AdjuntoService service;

    public AdjuntoController(AdjuntoService service) {
        this.service = service;
    }

    // ── Listados ──────────────────────────────────────────────────────────────

    @GetMapping("/paciente/{pacienteId}")
    @Operation(summary = "Listar archivos de un paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de archivos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public List<AdjuntoResponse> listarPorPaciente(@PathVariable UUID pacienteId) {
        return service.listarPorPaciente(pacienteId);
    }

    @GetMapping("/cita/{citaId}")
    @Operation(summary = "Listar archivos y estudios asociados a una cita")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de archivos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<AdjuntoResponse> listarPorCita(@PathVariable UUID citaId) {
        return service.listarPorCita(citaId);
    }

    @GetMapping("/mis-estudios")
    @Operation(summary = "Listar mis estudios solicitados (paciente autenticado)",
               description = "Devuelve todos los estudios del paciente autenticado, ordenados del más reciente al más antiguo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de estudios"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.PACIENTE + "')")
    public List<AdjuntoResponse> listarMisEstudios() {
        return service.listarMisEstudios();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar adjunto por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo encontrado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Archivo no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public AdjuntoResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    // ── Workflow estudios ─────────────────────────────────────────────────────

    @PostMapping("/solicitar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar un estudio médico al paciente",
               description = "El médico o admin crea un estudio en estado SOLICITADO. "
                           + "El paciente lo verá en su portal y podrá subir el resultado.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Estudio solicitado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public AdjuntoResponse solicitar(@Valid @RequestBody SolicitudEstudioRequest req) {
        return service.solicitar(req);
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Paciente sube el archivo directamente desde su dispositivo",
        description = "Recibe multipart/form-data con campo «archivo». "
                    + "Valida extensión (pdf, jpg, jpeg, png, webp) y MIME de forma independiente. "
                    + "El archivo se almacena en Supabase Storage. El estudio pasa a SUBIDO "
                    + "y se crea una notificación para el médico. Tamaño máximo: 10 MB."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo cargado"),
        @ApiResponse(responseCode = "400", description = "Extensión o MIME no permitido / estado no es SOLICITADO"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "No eres el dueño del estudio"),
        @ApiResponse(responseCode = "404", description = "Estudio no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error al subir a Supabase Storage")
    })
    @PreAuthorize("hasRole('" + Roles.PACIENTE + "')")
    public AdjuntoResponse upload(
            @PathVariable UUID id,
            @RequestParam("archivo") MultipartFile archivo) {
        return service.uploadArchivo(id, archivo);
    }

    @Deprecated
    @PatchMapping("/{id}/cargar")
    @Operation(
        summary = "Paciente sube el resultado por URL (obsoleto)",
        description = "⚠ **Obsoleto.** Usar `POST /{id}/upload` con multipart para carga directa. "
                    + "Mantenido solo por compatibilidad. "
                    + "Body: { \"url\": \"...\", \"nombre\": \"...\", \"mimeType\": \"...\" }",
        deprecated = true
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado cargado"),
        @ApiResponse(responseCode = "400", description = "El estudio no está en estado SOLICITADO"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos o no eres el dueño del estudio"),
        @ApiResponse(responseCode = "404", description = "Estudio no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.PACIENTE + "')")
    public AdjuntoResponse cargar(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.cargarArchivo(
                id,
                body.get("url"),
                body.get("nombre"),
                body.get("mimeType")
        );
    }

    @PatchMapping("/{id}/revisar")
    @Operation(summary = "Médico revisa el resultado del estudio",
               description = "Deja un comentario y pasa el estado a REVISADO. "
                           + "Body: { \"comentario\": \"...\", \"estado\": \"REVISADO\" }")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estudio revisado"),
        @ApiResponse(responseCode = "400", description = "El estudio no está en estado SUBIDO o estado inválido"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Estudio no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public AdjuntoResponse revisar(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return service.revisar(
                id,
                body.get("comentario"),
                body.getOrDefault("estado", "REVISADO")
        );
    }

    // ── CRUD existente ────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un adjunto directamente",
               description = "Almacena la URL del archivo y sus metadatos. Para el flujo guiado usa POST /solicitar.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Archivo creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public AdjuntoResponse crear(@Valid @RequestBody AdjuntoRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos de un adjunto")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Archivo no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public AdjuntoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody AdjuntoRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un adjunto")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Archivo eliminado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Archivo no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
