package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.MedicoRequest;
import com.clinica.mi_app.dto.response.MedicoResponse;
import com.clinica.mi_app.service.MedicoService;
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
@RequestMapping("/api/medicos")
@Tag(name = "Médicos", description = "Gestión de médicos")
@SecurityRequirement(name = "bearerAuth")
public class MedicoController {

    private final MedicoService service;

    public MedicoController(MedicoService service) {
        this.service = service;
    }

    @GetMapping("/organizacion/{orgId}")
    @Operation(summary = "Listar médicos de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de médicos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<MedicoResponse> listarPorOrganizacion(@PathVariable UUID orgId) {
        return service.listarPorOrganizacion(orgId);
    }

    @GetMapping("/organizacion/{orgId}/activos")
    @Operation(summary = "Listar solo médicos activos de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de médicos activos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public List<MedicoResponse> listarActivos(@PathVariable UUID orgId) {
        return service.listarActivosPorOrganizacion(orgId);
    }

    @GetMapping("/consultorio/{consultorioId}")
    @Operation(summary = "Listar médicos asignados a un consultorio")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de médicos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<MedicoResponse> listarPorConsultorio(@PathVariable UUID consultorioId) {
        return service.listarPorConsultorio(consultorioId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar médico por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Médico encontrado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Médico no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public MedicoResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar nuevo médico")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Médico creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public MedicoResponse crear(@Valid @RequestBody MedicoRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos de un médico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Médico actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Médico no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public MedicoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody MedicoRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Alternar estado activo/inactivo de un médico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Médico no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public MedicoResponse toggleActivo(@PathVariable UUID id) {
        return service.toggleActivo(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un médico")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Médico eliminado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Médico no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
