package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.ConsultorioRequest;
import com.clinica.mi_app.dto.response.ConsultorioResponse;
import com.clinica.mi_app.service.ConsultorioService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/consultorios")
@Tag(name = "Consultorios", description = "Gestión de consultorios")
@SecurityRequirement(name = "bearerAuth")
public class ConsultorioController {

    private final ConsultorioService service;

    public ConsultorioController(ConsultorioService service) {
        this.service = service;
    }

    @GetMapping("/organizacion/{orgId}")
    @Operation(summary = "Listar consultorios de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de consultorios"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<ConsultorioResponse> listarPorOrganizacion(@PathVariable UUID orgId) {
        return service.listarPorOrganizacion(orgId);
    }

    @GetMapping("/organizacion/{orgId}/activos")
    @Operation(summary = "Listar solo consultorios activos de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de consultorios activos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<ConsultorioResponse> listarActivos(@PathVariable UUID orgId) {
        return service.listarActivosPorOrganizacion(orgId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar consultorio por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consultorio encontrado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Consultorio no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public ConsultorioResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nuevo consultorio")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Consultorio creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ConsultorioResponse crear(@Valid @RequestBody ConsultorioRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos de un consultorio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consultorio actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Consultorio no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ConsultorioResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ConsultorioRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Activar o desactivar un consultorio")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public ConsultorioResponse toggleActivo(@PathVariable UUID id) {
        return service.toggleActivo(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un consultorio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Consultorio eliminado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Consultorio no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
