package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mensaje que el usuario escribe "como paciente" en el canal de pruebas. */
public record SimuladorRespuestaRequest(
        @NotBlank(message = "El teléfono es obligatorio") String telefono,
        @NotBlank(message = "El texto es obligatorio") @Size(max = 1000) String texto) {}
