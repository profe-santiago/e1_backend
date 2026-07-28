package com.clinica.mi_app.dto.response;

public class DisponibilidadSlot {
    private final String hora;
    private final String horaFin;
    private final int duracionMin;

    public DisponibilidadSlot(String hora, String horaFin, int duracionMin) {
        this.hora = hora;
        this.horaFin = horaFin;
        this.duracionMin = duracionMin;
    }

    public String getHora() { return hora; }
    public String getHoraFin() { return horaFin; }
    public int getDuracionMin() { return duracionMin; }
}
