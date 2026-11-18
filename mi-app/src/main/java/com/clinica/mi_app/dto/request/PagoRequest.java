package com.clinica.mi_app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class PagoRequest {

    @NotNull
    private UUID organizacionId;

    @NotNull
    private UUID citaId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monto;

    @NotBlank
    @Size(max = 20)
    private String metodo;

    @Size(max = 200)
    private String concepto;

    @Size(max = 100)
    private String referencia;

    /** Opcional: PAGADO | PENDIENTE. Si es null se conserva el estado actual (PENDIENTE al crear). */
    @Pattern(regexp = "PAGADO|PENDIENTE", message = "Estado de pago inválido: usa PAGADO o PENDIENTE")
    private String estado;

    public UUID getOrganizacionId() { return organizacionId; }
    public void setOrganizacionId(UUID organizacionId) { this.organizacionId = organizacionId; }

    public UUID getCitaId() { return citaId; }
    public void setCitaId(UUID citaId) { this.citaId = citaId; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
