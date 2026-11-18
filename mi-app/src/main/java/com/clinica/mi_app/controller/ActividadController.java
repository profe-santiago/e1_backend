package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.response.ActividadResponse;
import com.clinica.mi_app.dto.response.ProximoDiaResponse;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import com.clinica.mi_app.service.ActividadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Vista "Actividad". La organización sale siempre del JWT. El MEDICO solo ve sus citas (el parámetro
 * medicoId se ignora); el ADMIN ve toda la clínica o filtra por un médico de su organización.
 */
@RestController
@RequestMapping("/api/notificaciones/actividad")
@Tag(name = "Actividad", description = "Respuestas de pacientes y recordatorios enviados")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
public class ActividadController {

    private final ActividadService service;

    public ActividadController(ActividadService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Avisos de los últimos 30 días (máx. 200)",
            description = "filtro: SIN_LEER | CONFIRMACIONES | CANCELACIONES | RECORDATORIOS (vacío = todas)")
    public ActividadResponse actividad(@RequestParam(required = false) String filtro,
                                       @RequestParam(required = false) UUID medicoId) {
        return service.actividad(AuthenticatedUser.getOrganizacionId(), medicoEfectivo(medicoId), filtro);
    }

    @GetMapping("/manana")
    @Operation(summary = "Resumen del próximo día con citas (desde mañana, hasta 7 días)")
    public ProximoDiaResponse manana(@RequestParam(required = false) UUID medicoId) {
        return service.proximoDia(AuthenticatedUser.getOrganizacionId(), medicoEfectivo(medicoId));
    }

    @PostMapping("/leidas")
    @Operation(summary = "Marcar como leídos todos los avisos de la actividad")
    public Map<String, Integer> marcarTodas(@RequestParam(required = false) UUID medicoId) {
        return Map.of("marcadas", service.marcarTodasLeidas(AuthenticatedUser.getOrganizacionId(), medicoEfectivo(medicoId)));
    }

    /** MEDICO: siempre su propio id. ADMIN: el filtro opcional. */
    static UUID medicoEfectivo(UUID solicitado) {
        return Roles.MEDICO.equals(AuthenticatedUser.getRol()) ? AuthenticatedUser.getMedicoId() : solicitado;
    }
}
