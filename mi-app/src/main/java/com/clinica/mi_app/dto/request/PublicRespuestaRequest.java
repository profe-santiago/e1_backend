package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Respuesta del paciente desde el enlace: CONFIRMAR o CANCELAR. */
public record PublicRespuestaRequest(
        @NotBlank(message = "Indica la acción")
        @Pattern(regexp = "CONFIRMAR|CANCELAR", message = "Acción inválida: usa CONFIRMAR o CANCELAR")
        String accion) {}
