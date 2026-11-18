package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.response.CobrosResumenResponse;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import com.clinica.mi_app.service.CobroResumenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cobros")
@Tag(name = "Cobros", description = "Resumen de ingresos")
@SecurityRequirement(name = "bearerAuth")
public class CobroController {

    private final CobroResumenService service;

    public CobroController(CobroResumenService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de cobros PAGADO en un rango",
            description = "desde/hasta en formato YYYY-MM-DD (inclusive). Por defecto, los últimos 30 días. "
                    + "Total, desglose por método, ingresos por día y por médico. La organización se toma del JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumen"),
        @ApiResponse(responseCode = "400", description = "Rango inválido"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Solo ADMIN")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public CobrosResumenResponse resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.resumen(AuthenticatedUser.getOrganizacionId(), desde, hasta);
    }
}
