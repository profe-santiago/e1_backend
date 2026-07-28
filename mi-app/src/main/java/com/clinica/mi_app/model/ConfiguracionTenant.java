package com.clinica.mi_app.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "configuracion_tenant")
public class ConfiguracionTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizacion_id", nullable = false, unique = true)
    private Organizacion organizacion;

    @Column(name = "notif_api_key")
    private String notifApiKey;

    @Column(name = "storage_api_key")
    private String storageApiKey;

    @Column(name = "payment_api_key")
    private String paymentApiKey;

    @Column(name = "audit_api_key")
    private String auditApiKey;

    @Column(name = "whatsapp_conectado", nullable = false)
    private Boolean whatsappConectado = false;

    @Column(name = "whatsapp_numero", length = 20)
    private String whatsappNumero;

    // ---------- Getters y Setters ----------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Organizacion getOrganizacion() { return organizacion; }
    public void setOrganizacion(Organizacion organizacion) { this.organizacion = organizacion; }

    public String getNotifApiKey() { return notifApiKey; }
    public void setNotifApiKey(String notifApiKey) { this.notifApiKey = notifApiKey; }

    public String getStorageApiKey() { return storageApiKey; }
    public void setStorageApiKey(String storageApiKey) { this.storageApiKey = storageApiKey; }

    public String getPaymentApiKey() { return paymentApiKey; }
    public void setPaymentApiKey(String paymentApiKey) { this.paymentApiKey = paymentApiKey; }

    public String getAuditApiKey() { return auditApiKey; }
    public void setAuditApiKey(String auditApiKey) { this.auditApiKey = auditApiKey; }

    public Boolean getWhatsappConectado() { return whatsappConectado; }
    public void setWhatsappConectado(Boolean whatsappConectado) { this.whatsappConectado = whatsappConectado; }

    public String getWhatsappNumero() { return whatsappNumero; }
    public void setWhatsappNumero(String whatsappNumero) { this.whatsappNumero = whatsappNumero; }
}
