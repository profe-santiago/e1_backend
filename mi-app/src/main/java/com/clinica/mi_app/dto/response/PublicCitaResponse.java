package com.clinica.mi_app.dto.response;

import java.time.OffsetDateTime;

/**
 * Datos mínimos de la cita para la página pública /c/{token}: sin el teléfono del paciente ni datos clínicos.
 * {@code fechaHora} sigue la convención de reloj de la clínica en UTC. {@code resultado} solo
 * viene después de responder (CONFIRMADA | CANCELADA). {@code telefonoClinica} es el teléfono público del
 * consultorio (para "Llamar a la clínica"), nunca el del paciente.
 */
public record PublicCitaResponse(String pacienteNombre, String clinica, String medico, String especialidad,
                                 String consultorio, String direccion, OffsetDateTime fechaHora,
                                 String estado, String resultado, Short duracionMin, String telefonoClinica) {}
