package com.clinica.mi_app.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Enlace de un solo uso para que el paciente confirme o cancele su cita sin iniciar sesión. */
@Entity
@Table(name = "cita_enlace")
public class CitaEnlace {

    public static final String CONFIRMAR = "CONFIRMAR";
    public static final String CANCELAR = "CANCELAR";
    /** Se envió un recordatorio más reciente para la misma cita. */
    public static final String REEMPLAZADO = "REEMPLAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizacion_id", nullable = false)
    private Organizacion organizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    /** SHA-256 (hex) del token; el token en claro solo viaja en la URL. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** Hora de la cita, en la convención de reloj de la clínica expresada en UTC. */
    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    @Column(length = 20)
    private String accion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Organizacion getOrganizacion() { return organizacion; }
    public void setOrganizacion(Organizacion organizacion) { this.organizacion = organizacion; }

    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(OffsetDateTime expiraEn) { this.expiraEn = expiraEn; }

    public OffsetDateTime getUsadoEn() { return usadoEn; }
    public void setUsadoEn(OffsetDateTime usadoEn) { this.usadoEn = usadoEn; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
