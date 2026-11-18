package com.clinica.mi_app.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizacion_id", nullable = false)
    private Organizacion organizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjunto_id")
    private Adjunto adjunto; // nullable

    @Column(nullable = false, length = 20)
    private String canal = "WHATSAPP";

    @Column(nullable = false, length = 40)
    private String tipo; // RECORDATORIO_48H | RECORDATORIO_24H | RECORDATORIO_MANUAL | ADJUNTO | CANCELACION_PACIENTE | CONFIRMACION_PACIENTE

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE"; // PENDIENTE | ENVIADO | FALLIDO

    @Column(name = "enviada_en")
    private OffsetDateTime enviadaEn;

    @Column(columnDefinition = "TEXT")
    private String respuesta;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn = OffsetDateTime.now();

    @Column(nullable = false)
    private Boolean leida = false;

    // ---------- Getters y Setters ----------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Organizacion getOrganizacion() { return organizacion; }
    public void setOrganizacion(Organizacion organizacion) { this.organizacion = organizacion; }

    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }

    public Adjunto getAdjunto() { return adjunto; }
    public void setAdjunto(Adjunto adjunto) { this.adjunto = adjunto; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getEnviadaEn() { return enviadaEn; }
    public void setEnviadaEn(OffsetDateTime enviadaEn) { this.enviadaEn = enviadaEn; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }

    public OffsetDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(OffsetDateTime creadaEn) { this.creadaEn = creadaEn; }

    public Boolean getLeida() { return leida; }
    public void setLeida(Boolean leida) { this.leida = leida; }
}
