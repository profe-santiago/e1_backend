package com.clinica.mi_app.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una conversación por teléfono de paciente, con su último mensaje. */
public record ConversacionResumenResponse(String telefono, String pacienteNombre, UUID citaId, String ultimoTexto,
                                          String ultimaDireccion, OffsetDateTime ultimaFecha, long mensajes) {}
