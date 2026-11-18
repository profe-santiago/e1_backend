package com.clinica.mi_app.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser del webhook de WhatsApp Cloud API (formato oficial de Meta):
 * {@code entry[].changes[].value.messages[]} y {@code entry[].changes[].value.statuses[]}.
 * Lo usan el webhook real de Meta y el simulador (que construye un payload idéntico).
 */
@Component
public class MetaWebhookParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<MensajeEntrante> mensajes(String json) {
        List<MensajeEntrante> resultado = new ArrayList<>();
        for (JsonNode value : values(json)) {
            for (JsonNode msg : value.path("messages")) {
                resultado.add(new MensajeEntrante(
                        texto(msg, "id"),
                        TelefonoMx.aDiezDigitos(texto(msg, "from")),
                        contenido(msg),
                        texto(msg.path("context"), "id")));
            }
        }
        return resultado;
    }

    public List<EstadoEntrega> estados(String json) {
        List<EstadoEntrega> resultado = new ArrayList<>();
        for (JsonNode value : values(json)) {
            for (JsonNode st : value.path("statuses")) {
                String estado = EstadoEntrega.traducir(texto(st, "status"));
                if (estado != null) resultado.add(new EstadoEntrega(texto(st, "id"), estado));
            }
        }
        return resultado;
    }

    /** text.body | button.payload/text | interactive.button_reply/list_reply (id, luego title). */
    static String contenido(JsonNode msg) {
        String tipo = msg.path("type").asText("");
        return switch (tipo) {
            case "text" -> texto(msg.path("text"), "body");
            case "button" -> primero(texto(msg.path("button"), "payload"), texto(msg.path("button"), "text"));
            case "interactive" -> {
                JsonNode i = msg.path("interactive");
                JsonNode r = i.has("button_reply") ? i.path("button_reply") : i.path("list_reply");
                yield primero(texto(r, "id"), texto(r, "title"));
            }
            default -> "[" + (tipo.isBlank() ? "mensaje" : tipo) + "]";
        };
    }

    private List<JsonNode> values(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "{}" : json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Payload de WhatsApp no es JSON válido");
        }
        List<JsonNode> values = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                values.add(change.path("value"));
            }
        }
        return values;
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String primero(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
