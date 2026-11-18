package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.SimuladorRespuestaRequest;
import com.clinica.mi_app.dto.response.ConversacionResumenResponse;
import com.clinica.mi_app.dto.response.RecordatorioEstadoResponse;
import com.clinica.mi_app.dto.response.WhatsAppCanalResponse;
import com.clinica.mi_app.dto.response.WhatsAppMensajeResponse;
import com.clinica.mi_app.security.AuthenticatedUser;
import com.clinica.mi_app.security.Roles;
import com.clinica.mi_app.whatsapp.WhatsAppConsultaService;
import com.clinica.mi_app.whatsapp.WhatsAppMensajeria;
import com.clinica.mi_app.whatsapp.WhatsAppSimuladorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conversaciones de WhatsApp y canal de pruebas. Todo acotado a la organización del JWT. */
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp", description = "Conversaciones, estado de recordatorios y simulador")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.MEDICO + "')")
public class WhatsAppController {

    private final WhatsAppConsultaService consulta;
    private final WhatsAppSimuladorService simulador;
    private final WhatsAppMensajeria mensajeria;

    public WhatsAppController(WhatsAppConsultaService consulta, WhatsAppSimuladorService simulador,
                              WhatsAppMensajeria mensajeria) {
        this.consulta = consulta;
        this.simulador = simulador;
        this.mensajeria = mensajeria;
    }

    @GetMapping("/canal")
    @Operation(summary = "Proveedor de WhatsApp activo")
    public WhatsAppCanalResponse canal() {
        return new WhatsAppCanalResponse(mensajeria.proveedor(), esSimulado());
    }

    @GetMapping("/conversaciones")
    @Operation(summary = "Historial de mensajes por cita o por teléfono")
    public List<WhatsAppMensajeResponse> conversacion(@RequestParam(required = false) UUID citaId,
                                                      @RequestParam(required = false) String telefono) {
        return consulta.mensajes(AuthenticatedUser.getOrganizacionId(), citaId, telefono);
    }

    @GetMapping("/conversaciones/resumen")
    @Operation(summary = "Lista de conversaciones (una por paciente/teléfono)")
    public List<ConversacionResumenResponse> resumen() {
        return consulta.resumen(AuthenticatedUser.getOrganizacionId());
    }

    @GetMapping("/recordatorios/estado")
    @Operation(summary = "Último estado del recordatorio por cita (ENVIADO, FALLIDO o RESPONDIO)")
    public Map<UUID, RecordatorioEstadoResponse> estadoRecordatorios(@RequestParam List<UUID> citaIds) {
        return consulta.estadoRecordatorios(AuthenticatedUser.getOrganizacionId(), citaIds);
    }

    @PostMapping("/simulador/responder")
    @Operation(summary = "Responder como paciente (solo canal simulado)",
            description = "Construye un payload idéntico al webhook de Meta y lo procesa por el mismo flujo")
    public ResponseEntity<List<WhatsAppMensajeResponse>> responder(@Valid @RequestBody SimuladorRespuestaRequest req) {
        if (!esSimulado()) return ResponseEntity.notFound().build();
        UUID org = AuthenticatedUser.getOrganizacionId();
        simulador.responder(org, req.telefono(), req.texto());
        return ResponseEntity.ok(consulta.mensajes(org, null, req.telefono()));
    }

    private boolean esSimulado() {
        return "simulado".equals(mensajeria.proveedor());
    }
}
