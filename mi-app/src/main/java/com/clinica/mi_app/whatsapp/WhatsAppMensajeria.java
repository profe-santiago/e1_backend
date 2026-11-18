package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.WhatsAppMensaje;
import com.clinica.mi_app.repository.WhatsAppMensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fachada única de WhatsApp: delega en el {@link WhatsAppSender} activo y registra <b>siempre</b> cada
 * mensaje (saliente o entrante) en whatsapp_mensaje, sea cual sea el proveedor. Así el panel de
 * conversaciones y la resolución de respuestas funcionan igual en simulado, Twilio, Meta o InToGlobe.
 */
@Service
public class WhatsAppMensajeria {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMensajeria.class);

    /** Orden de avance de estados de un saliente; FALLIDO siempre se aplica. */
    private static final Map<String, Integer> RANGO = Map.of("ENVIADO", 1, "ENTREGADO", 2, "LEIDO", 3);

    private final WhatsAppSender sender;
    private final WhatsAppMensajeRepository repo;
    private final String plantillaRecordatorio;

    public WhatsAppMensajeria(WhatsAppSender sender, WhatsAppMensajeRepository repo,
                              @Value("${whatsapp.meta.template-recordatorio:recordatorio_cita}") String plantillaRecordatorio) {
        this.sender = sender;
        this.repo = repo;
        this.plantillaRecordatorio = plantillaRecordatorio;
    }

    public String proveedor() {
        return sender.proveedor();
    }

    /** Envía el recordatorio de la cita al teléfono del paciente. Devuelve el id del proveedor o vacío. */
    public Optional<String> enviarRecordatorio(Cita cita) {
        String telefono = TelefonoMx.aDiezDigitos(cita.getPaciente().getTelefono());
        List<String> params = RecordatorioPlantilla.parametros(cita);
        Organizacion org = cita.getOrganizacion();
        Optional<String> id = seguro(() -> sender.sendTemplate(org.getId(), telefono, plantillaRecordatorio, params));
        registrarSaliente(org, cita, telefono, RecordatorioPlantilla.renderizar(params), id);
        return id;
    }

    /** Texto libre (respuestas al paciente). */
    public Optional<String> enviarTexto(Organizacion org, Cita cita, String telefono, String texto) {
        Optional<String> id = seguro(() -> sender.sendText(org.getId(), telefono, texto));
        registrarSaliente(org, cita, telefono, texto, id);
        return id;
    }

    public void registrarEntrante(Organizacion org, Cita cita, String telefono, String texto, String id) {
        WhatsAppMensaje m = nuevo(org, cita, telefono, WhatsAppMensaje.ENTRANTE, texto);
        m.setWamid(id);
        m.setEstado("RECIBIDO");
        repo.save(m);
    }

    /** Aplica un status callback del proveedor al mensaje saliente correspondiente. */
    public void actualizarEstado(String id, String estado) {
        if (id == null || estado == null) return;
        repo.findFirstByWamid(id)
                .filter(m -> WhatsAppMensaje.SALIENTE.equals(m.getDireccion()))
                .filter(m -> "FALLIDO".equals(estado)
                        || RANGO.getOrDefault(estado, 0) > RANGO.getOrDefault(m.getEstado(), 0))
                .ifPresent(m -> {
                    m.setEstado(estado);
                    repo.save(m);
                });
    }

    private void registrarSaliente(Organizacion org, Cita cita, String telefono, String texto, Optional<String> id) {
        WhatsAppMensaje m = nuevo(org, cita, telefono, WhatsAppMensaje.SALIENTE, texto);
        m.setWamid(id.orElse(null));
        m.setEstado(id.isPresent() ? "ENVIADO" : "FALLIDO");
        repo.save(m);
    }

    private static WhatsAppMensaje nuevo(Organizacion org, Cita cita, String telefono, String direccion, String texto) {
        WhatsAppMensaje m = new WhatsAppMensaje();
        m.setOrganizacion(org);
        m.setCita(cita);
        m.setTelefono(telefono);
        m.setDireccion(direccion);
        m.setTexto(texto == null ? "" : texto);
        return m;
    }

    /** El contrato de WhatsAppSender ya prohíbe excepciones; esto protege contra implementaciones que fallen. */
    private Optional<String> seguro(java.util.function.Supplier<Optional<String>> envio) {
        try {
            Optional<String> r = envio.get();
            return r == null ? Optional.empty() : r;
        } catch (RuntimeException ex) {
            log.warn("WhatsAppSender {} lanzó {}: {}", sender.proveedor(), ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }
}
