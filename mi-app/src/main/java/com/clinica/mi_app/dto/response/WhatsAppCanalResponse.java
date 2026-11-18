package com.clinica.mi_app.dto.response;

/** Proveedor activo; el frontend muestra el simulador solo cuando simulado = true. */
public record WhatsAppCanalResponse(String proveedor, boolean simulado) {}
