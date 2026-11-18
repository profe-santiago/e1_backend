package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.NotificacionRequest;
import com.clinica.mi_app.dto.response.CampanaResponse;
import com.clinica.mi_app.dto.response.NotificacionResponse;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.clinica.mi_app.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notificaciones")
@Tag(name = "Notificaciones", description = "Notificaciones WhatsApp")
@SecurityRequirement(name = "bearerAuth")
public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @GetMapping("/recientes")
    @Operation(summary = "Campana: cancelaciones recientes del paciente y contador de no leídas",
            description = "ADMIN ve toda su organización; MEDICO solo las de sus citas")
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public CampanaResponse recientes() {
        return service.campana(AuthenticatedUser.getOrganizacionId(), medicoRestringido());
    }

    @PatchMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar una notificación como leída")
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public void marcarLeida(@PathVariable UUID id) {
        service.marcarLeida(AuthenticatedUser.getOrganizacionId(), medicoRestringido(), id);
    }

    @PostMapping("/leidas")
    @Operation(summary = "Marcar todas las cancelaciones como leídas")
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public Map<String, Integer> marcarTodas() {
        return Map.of("marcadas", service.marcarTodasLeidas(AuthenticatedUser.getOrganizacionId(), medicoRestringido()));
    }

    private static UUID medicoRestringido() {
        return Roles.MEDICO.equals(AuthenticatedUser.getRol()) ? AuthenticatedUser.getMedicoId() : null;
    }

    @GetMapping("/cita/{citaId}")
    @Operation(summary = "Listar notificaciones de una cita")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<NotificacionResponse> listarPorCita(@PathVariable UUID citaId) {
        return service.listarPorCita(citaId);
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Listar todas las notificaciones pendientes de envío")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de notificaciones"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public List<NotificacionResponse> listarPendientes() {
        return service.listarPendientes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar notificación por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificación encontrada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public NotificacionResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear notificación", description = "Tipos válidos: RECORDATORIO_48H, RECORDATORIO_24H, ADJUNTO. Canal por defecto: WHATSAPP")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Notificación creada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public NotificacionResponse crear(@Valid @RequestBody NotificacionRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar canal o tipo de una notificación")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificación actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public NotificacionResponse actualizar(@PathVariable UUID id, @Valid @RequestBody NotificacionRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una notificación")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Notificación eliminada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
