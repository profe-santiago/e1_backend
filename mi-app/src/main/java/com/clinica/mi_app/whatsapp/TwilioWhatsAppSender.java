package com.clinica.mi_app.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WhatsApp vía Twilio (sandbox o número aprobado).
 * POST {api}/2010-04-01/Accounts/{SID}/Messages.json, form-urlencoded (From, To, Body), Basic SID:token.
 *
 * <p>En el sandbox no hay plantillas aprobadas: {@link #sendTemplate} manda el texto ya renderizado de
 * {@link RecordatorioPlantilla}. El SID devuelto (SM...) se usa como id del mensaje.
 */
@Component
@ConditionalOnProperty(name = "notif.provider", havingValue = "twilio")
public class TwilioWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppSender.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final String CIRCUIT_BREAKER = "whatsapp-twilio";

    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String statusCallback;
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public TwilioWhatsAppSender(@Value("${whatsapp.twilio.account-sid:}") String accountSid,
                                @Value("${whatsapp.twilio.auth-token:}") String authToken,
                                @Value("${whatsapp.twilio.from:whatsapp:+17372508034}") String from,
                                @Value("${whatsapp.twilio.webhook-url:}") String webhookUrl,
                                @Value("${whatsapp.twilio.api-base-url:https://api.twilio.com}") String apiBaseUrl,
                                ObjectProvider<CircuitBreakerRegistry> registry) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.statusCallback = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl + "/status";
        this.webClient = WebClient.builder().baseUrl(apiBaseUrl).build();
        this.circuitBreaker = registry.getIfAvailable(CircuitBreakerRegistry::ofDefaults).circuitBreaker(CIRCUIT_BREAKER);
    }

    @Override
    public String proveedor() {
        return "twilio";
    }

    @Override
    public Optional<String> sendTemplate(UUID organizacionId, String telefono, String plantilla, List<String> params) {
        return enviar(telefono, RecordatorioPlantilla.renderizar(params));
    }

    @Override
    public Optional<String> sendText(UUID organizacionId, String telefono, String texto) {
        return enviar(telefono, texto);
    }

    private Optional<String> enviar(String telefono, String body) {
        if (accountSid.isBlank() || authToken.isBlank()) {
            log.warn("Twilio: faltan whatsapp.twilio.account-sid / auth-token; mensaje no enviado");
            return Optional.empty();
        }
        String e164 = TelefonoMx.aE164(telefono);
        if (e164 == null) {
            log.warn("Twilio: teléfono inválido, mensaje no enviado");
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", "whatsapp:" + e164);
        form.add("Body", body);
        if (statusCallback != null) form.add("StatusCallback", statusCallback);

        try {
            String respuesta = circuitBreaker.executeSupplier(() -> webClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT));
            JsonNode json = mapper.readTree(respuesta == null ? "{}" : respuesta);
            String sid = json.path("sid").asText(null);
            if (sid == null || sid.isBlank()) {
                log.warn("Twilio: respuesta sin sid");
                return Optional.empty();
            }
            return Optional.of(sid);
        } catch (WebClientResponseException ex) {
            // El cuerpo de error de Twilio no contiene credenciales; el token nunca se registra
            log.warn("Twilio respondió {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Twilio: envío fallido ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }
}
