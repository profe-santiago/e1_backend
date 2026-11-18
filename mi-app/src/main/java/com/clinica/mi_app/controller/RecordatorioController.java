package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.response.EnvioRecordatorioResponse;
import com.clinica.mi_app.model.Notificacion;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import com.clinica.mi_app.service.RecordatorioService;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/recordatorios")
@Tag(name = "Recordatorios", description = "Envío manual de recordatorios de cita por WhatsApp")
@SecurityRequirement(name = "bearerAuth")
public class RecordatorioController {

    private final RecordatorioService service;
    private final WhatsAppMensajeria mensajeria;

    public RecordatorioController(RecordatorioService service, WhatsAppMensajeria mensajeria) {
        this.service = service;
        this.mensajeria = mensajeria;
    }

    @PostMapping("/{citaId}/enviar")
    @Operation(summary = "Enviar recordatorio de WhatsApp de una cita",
            description = "Mismas validaciones que el job. Sin reenviar=true no duplica un recordatorio ya enviado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Enviado"),
        @ApiResponse(responseCode = "400", description = "Cita cancelada, no asistida, pasada o paciente sin teléfono"),
        @ApiResponse(responseCode = "404", description = "Cita no encontrada en tu organización"),
        @ApiResponse(responseCode = "409", description = "Ya se envió un recordatorio"),
        @ApiResponse(responseCode = "502", description = "El proveedor de WhatsApp falló (queda registrado como FALLIDO)")
    })
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
    public ResponseEntity<EnvioRecordatorioResponse> enviar(@PathVariable UUID citaId,
                                                            @RequestParam(defaultValue = "false") boolean reenviar) {
        UUID medicoId = Roles.MEDICO.equals(AuthenticatedUser.getRol()) ? AuthenticatedUser.getMedicoId() : null;
        RecordatorioService.EnvioRecordatorio envio =
                service.enviarManualDetalle(AuthenticatedUser.getOrganizacionId(), medicoId, citaId, reenviar);
        Notificacion n = envio.notificacion();
        HttpStatus status = "ENVIADO".equals(n.getEstado()) ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(EnvioRecordatorioResponse.of(n, mensajeria.proveedor(), envio.waUrl()));
    }
}
