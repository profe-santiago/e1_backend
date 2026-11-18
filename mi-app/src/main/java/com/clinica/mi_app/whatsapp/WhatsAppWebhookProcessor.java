package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import com.clinica.mi_app.service.WhatsAppRespuestaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Flujo único para mensajes entrantes, sin importar si vienen de Meta, Twilio o del simulador:
 * deduplica, resuelve la cita, registra el ENTRANTE y aplica la respuesta.
 *
 * <p>Resolución de la cita: (1) el saliente citado ({@code contextoId}), (2) si no, el último saliente a
 * ese teléfono cuya cita siga SIN_CONFIRMAR y en el futuro. Con {@code organizacionId} (simulador) la
 * búsqueda queda acotada a esa organización.
 */
@Service
public class WhatsAppWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookProcessor.class);

    public enum Resultado { PROCESADO, SIN_CITA_PENDIENTE, SIN_CONVERSACION, DUPLICADO, IGNORADO }

    private final WhatsAppMensajeRepository repo;
    private final WhatsAppMensajeria mensajeria;
    private final WhatsAppRespuestaService respuestaService;
    private final ClinicaReloj reloj;

    public WhatsAppWebhookProcessor(WhatsAppMensajeRepository repo, WhatsAppMensajeria mensajeria,
                                    WhatsAppRespuestaService respuestaService, ClinicaReloj reloj) {
        this.repo = repo;
        this.mensajeria = mensajeria;
        this.respuestaService = respuestaService;
        this.reloj = reloj;
    }

    @Transactional
    public Resultado procesar(MensajeEntrante m, UUID organizacionId) {
        if (m == null || m.telefono() == null) {
            log.info("WhatsApp: mensaje entrante sin teléfono MX reconocible; ignorado");
            return Resultado.IGNORADO;
        }
        if (m.id() != null && repo.existsByWamid(m.id())) {
            log.info("WhatsApp: mensaje {} ya procesado (reintento del proveedor)", m.id());
            return Resultado.DUPLICADO;
        }

        WhatsAppMensaje citado = m.contextoId() == null ? null : repo.findFirstByWamid(m.contextoId())
                .filter(x -> WhatsAppMensaje.SALIENTE.equals(x.getDireccion()))
                .filter(x -> organizacionId == null || organizacionId.equals(x.getOrganizacion().getId()))
                .orElse(null);
        List<WhatsAppMensaje> salientes = organizacionId == null
                ? repo.findByTelefonoAndDireccionOrderByCreatedAtDesc(m.telefono(), WhatsAppMensaje.SALIENTE)
                : repo.findByOrganizacionIdAndTelefonoAndDireccionOrderByCreatedAtDesc(
                        organizacionId, m.telefono(), WhatsAppMensaje.SALIENTE);

        Cita cita = citado != null && pendiente(citado.getCita()) ? citado.getCita() : null;
        if (cita == null) {
            cita = salientes.stream().map(WhatsAppMensaje::getCita).filter(Objects::nonNull)
                    .filter(this::pendiente).findFirst().orElse(null);
        }

        WhatsAppMensaje ancla = citado != null ? citado : (salientes.isEmpty() ? null : salientes.get(0));
        Organizacion org = cita != null ? cita.getOrganizacion() : (ancla == null ? null : ancla.getOrganizacion());
        if (org == null) {
            log.info("WhatsApp: mensaje de un teléfono sin conversación previa; ignorado");
            return Resultado.SIN_CONVERSACION;
        }

        mensajeria.registrarEntrante(org, cita != null ? cita : ancla.getCita(), m.telefono(), m.texto(), m.id());

        if (cita == null) {
            log.info("WhatsApp: respuesta sin cita SIN_CONFIRMAR futura; solo se registra");
            return Resultado.SIN_CITA_PENDIENTE;
        }
        respuestaService.procesarRespuesta(cita, m.telefono(), m.texto());
        return Resultado.PROCESADO;
    }

    @Transactional
    public void aplicarEstado(EstadoEntrega estado) {
        if (estado != null) mensajeria.actualizarEstado(estado.id(), estado.estado());
    }

    private boolean pendiente(Cita c) {
        return c != null && "SIN_CONFIRMAR".equals(c.getEstado()) && !c.getFechaHora().isBefore(reloj.ahora());
    }
}
