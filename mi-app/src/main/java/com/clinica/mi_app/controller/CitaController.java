package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.CitaRequest;
import com.clinica.mi_app.dto.response.CitaResponse;
import com.clinica.mi_app.dto.response.DisponibilidadSlot;
import com.clinica.mi_app.service.CitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import com.clinica.mi_app.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/citas")
@Tag(name = "Citas", description = "Agendamiento de citas médicas")
@SecurityRequirement(name = "bearerAuth")
public class CitaController {

    private final CitaService service;

    public CitaController(CitaService service) {
        this.service = service;
    }

    @GetMapping("/organizacion/{orgId}")
    @Operation(summary = "Listar todas las citas de una organización")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<CitaResponse> listarPorOrganizacion(@PathVariable UUID orgId) {
        return service.listarPorOrganizacion(orgId);
    }

    @GetMapping("/mis-citas")
    @Operation(summary = "Listar mis citas (paciente autenticado)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas del paciente"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    @PreAuthorize("hasRole('" + Roles.PACIENTE + "')")
    public List<CitaResponse> listarMisCitas() {
        return service.listarMisCitas();
    }

    @GetMapping("/medico/{medicoId}")
    @Operation(summary = "Listar citas de un médico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<CitaResponse> listarPorMedico(@PathVariable UUID medicoId) {
        return service.listarPorMedico(medicoId);
    }

    @GetMapping("/medico/{medicoId}/disponibilidad")
    @Operation(
        summary = "Slots disponibles de un médico para una fecha",
        description = "Devuelve los horarios libres según el horario laboral del médico y las citas ya registradas. "
                    + "Pasa excludeId para ignorar una cita existente al reagendar."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de slots disponibles"),
        @ApiResponse(responseCode = "400", description = "Parámetro fecha requerido"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<DisponibilidadSlot> disponibilidad(
            @PathVariable UUID medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) UUID excludeId) {
        return service.calcularDisponibilidad(medicoId, fecha, excludeId);
    }

    @GetMapping("/paciente/{pacienteId}")
    @Operation(summary = "Listar citas de un paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public List<CitaResponse> listarPorPaciente(@PathVariable UUID pacienteId) {
        return service.listarPorPaciente(pacienteId);
    }

    @GetMapping("/paciente/{pacienteId}/estado/{estado}")
    @Operation(summary = "Listar citas de un paciente por estado", description = "Estados válidos: SIN_CONFIRMAR, CONFIRMADA, CANCELADA, REAGENDADA, NO_ASISTIO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public List<CitaResponse> listarPorPacienteYEstado(@PathVariable UUID pacienteId, @PathVariable String estado) {
        return service.listarPorPacienteYEstado(pacienteId, estado);
    }

    @GetMapping("/organizacion/{orgId}/estado/{estado}")
    @Operation(summary = "Listar citas por estado", description = "Estados válidos: SIN_CONFIRMAR, CONFIRMADA, CANCELADA, REAGENDADA, NO_ASISTIO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de citas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public List<CitaResponse> listarPorEstado(@PathVariable UUID orgId, @PathVariable String estado) {
        return service.listarPorEstado(orgId, estado);
    }

    @GetMapping("/disponibilidad")
    @Operation(summary = "Listar horas disponibles de un medico para una fecha")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de horas disponibles"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public List<String> listarDisponibilidad(
            @RequestParam UUID medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Short duracionMin) {
        return service.listarHorasDisponibles(medicoId, fecha, duracionMin);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cita por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cita encontrada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "404", description = "Cita no encontrada")
    })
    public CitaResponse buscarPorId(@PathVariable UUID id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agendar nueva cita", description = "Valida disponibilidad del médico. La cita inicia con estado SIN_CONFIRMAR.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cita creada"),
        @ApiResponse(responseCode = "400", description = "Horario no disponible o fecha pasada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "', '" + Roles.PACIENTE + "')")
    public CitaResponse crear(@Valid @RequestBody CitaRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar fecha, duración o motivo de una cita", description = "Valida disponibilidad del nuevo horario.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cita actualizada"),
        @ApiResponse(responseCode = "400", description = "Horario no disponible o fecha pasada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Cita no encontrada")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public CitaResponse actualizar(@PathVariable UUID id, @Valid @RequestBody CitaRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado de una cita", description = "Estados válidos: SIN_CONFIRMAR, CONFIRMADA, CANCELADA, REAGENDADA, NO_ASISTIO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Estado no válido"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Cita no encontrada")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public CitaResponse cambiarEstado(@PathVariable UUID id, @RequestBody java.util.Map<String, String> body) {
        return service.cambiarEstado(id, body.get("estado"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una cita")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cita eliminada"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Cita no encontrada")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public void eliminar(@PathVariable UUID id) {
        service.buscarPorId(id);
        service.eliminar(id);
    }
}
