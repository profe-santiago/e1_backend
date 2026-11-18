package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.PagoRequest;
import com.clinica.mi_app.dto.response.PagoResponse;
import com.clinica.mi_app.service.PagoService;
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
@RequestMapping("/api/pagos")
@Tag(name = "Pagos", description = "Control de cobros y pagos")
@SecurityRequirement(name = "bearerAuth")
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    @GetMapping("/organizacion/{orgId}")
    @Operation(summary = "Listar todos los pagos de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pagos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<PagoResponse> listarPorOrganizacion(@PathVariable UUID orgId) {
        return service.listarPorOrganizacion(orgId);
    }

    @GetMapping("/organizacion/{orgId}/estado/{estado}")
    @Operation(summary = "Listar pagos por estado", description = "Estados válidos: PAGADO, PENDIENTE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pagos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<PagoResponse> listarPorEstado(@PathVariable UUID orgId, @PathVariable String estado) {
        return service.listarPorEstado(orgId, estado);
    }

    @GetMapping("/cita/{citaId}")
    @Operation(summary = "Listar los pagos asociados a una cita", description = "Devuelve [] si la cita no tiene pagos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pagos (vacía si no hay)"),
        @ApiResponse(responseCode = "401", description = "Token requerido")
    })
    public List<PagoResponse> listarPorCita(@PathVariable UUID citaId) {
        return service.listarPorCita(citaId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pago por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago encontrado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PagoResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un pago", description = "Métodos de pago válidos: EFECTIVO, TRANSFERENCIA, TARJETA")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pago creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PagoResponse crear(@Valid @RequestBody PagoRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos de un pago")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public PagoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody PagoRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un pago")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pago eliminado"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
