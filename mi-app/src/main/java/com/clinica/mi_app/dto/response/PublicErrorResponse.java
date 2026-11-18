package com.clinica.mi_app.dto.response;

/** Error de la página pública: código estable (NO_ENCONTRADO, USADO, EXPIRADO...) y mensaje para el paciente. */
public record PublicErrorResponse(int status, String codigo, String mensaje) {}
