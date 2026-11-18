package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.PacienteRequest;
import com.clinica.mi_app.dto.response.PacienteResponse;
import com.clinica.mi_app.service.PacienteService;
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
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes", description = "Gestión de pacientes del sistema")
@SecurityRequirement(name = "bearerAuth")
public class PacienteController {

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    @GetMapping("/organizacion/{orgId}")
    @Operation(summary = "Listar todos los pacientes de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pacientes"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<PacienteResponse> listarPorOrganizacion(@PathVariable UUID orgId) {
        return service.listarPorOrganizacion(orgId);
    }

    @GetMapping("/organizacion/{orgId}/activos")
    @Operation(summary = "Listar solo pacientes activos de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pacientes activos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<PacienteResponse> listarActivos(@PathVariable UUID orgId) {
        return service.listarActivosPorOrganizacion(orgId);
    }

    @GetMapping("/organizacion/{orgId}/buscar")
    @Operation(summary = "Buscar pacientes por nombre", description = "Búsqueda parcial e insensible a mayúsculas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pacientes"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<PacienteResponse> buscarPorNombre(@PathVariable UUID orgId, @RequestParam String nombre) {
        return service.buscarPorNombre(orgId, nombre);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar paciente por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paciente encontrado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public PacienteResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nuevo paciente", description = "Sexo permitido: M, F, O")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Paciente creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PacienteResponse crear(@Valid @RequestBody PacienteRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos de un paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paciente actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PacienteResponse actualizar(@PathVariable UUID id, @Valid @RequestBody PacienteRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Alternar estado activo/inactivo de un paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PacienteResponse toggleActivo(@PathVariable UUID id) {
        return service.toggleActivo(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Paciente eliminado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
