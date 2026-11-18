package com.clinica.mi_app.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Mensaje de WhatsApp registrado por la fachada WhatsAppMensajeria (cualquier proveedor). */
@Entity
@Table(name = "whatsapp_mensaje")
public class WhatsAppMensaje {

    public static final String SALIENTE = "SALIENTE";
    public static final String ENTRANTE = "ENTRANTE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizacion_id", nullable = false)
    private Organizacion organizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Cita cita; // nullable

    @Column(nullable = false, length = 20)
    private String telefono; // 10 dígitos MX

    @Column(nullable = false, length = 10)
    private String direccion; // SALIENTE | ENTRANTE

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(length = 128)
    private String wamid;

    @Column(nullable = false, length = 20)
    private String estado; // ENVIADO | FALLIDO | RECIBIDO | ENTREGADO | LEIDO

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Organizacion getOrganizacion() { return organizacion; }
    public void setOrganizacion(Organizacion organizacion) { this.organizacion = organizacion; }

    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public String getWamid() { return wamid; }
    public void setWamid(String wamid) { this.wamid = wamid; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
