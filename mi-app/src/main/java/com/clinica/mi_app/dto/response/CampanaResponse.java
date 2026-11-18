package com.clinica.mi_app.dto.response;

import java.util.List;

public record CampanaResponse(long noLeidas, List<NotificacionRecienteResponse> items) {}
