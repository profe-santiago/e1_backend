package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.response.AdminDashboardResponse;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import com.clinica.mi_app.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Panel del administrador")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Métricas del panel del administrador",
            description = "Citas de hoy por estado, ingresos de hoy, cobros pendientes, tasas de confirmación y "
                    + "no-asistencia (30 días) y próximas citas. La organización se toma del JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Métricas"),
        @ApiResponse(responseCode = "401", description = "Token requerido"),
        @ApiResponse(responseCode = "403", description = "Solo ADMIN")
    })
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public AdminDashboardResponse dashboard() {
        return service.dashboard(AuthenticatedUser.getOrganizacionId());
    }
}
