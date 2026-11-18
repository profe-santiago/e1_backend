package com.clinica.mi_app.whatsapp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetaWebhookParserTest {

    private final MetaWebhookParser parser = new MetaWebhookParser();

    private static String envolver(String value) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"WABA\",\"changes\":[{\"field\":\"messages\",\"value\":"
                + value + "}]}]}";
    }

    @Test
    void texto_conContexto() {
        String json = envolver("{\"messaging_product\":\"whatsapp\",\"metadata\":{\"phone_number_id\":\"123\"},"
                + "\"contacts\":[{\"profile\":{\"name\":\"Ana\"},\"wa_id\":\"5219511234567\"}],"
                + "\"messages\":[{\"from\":\"5219511234567\",\"id\":\"wamid.IN1\",\"timestamp\":\"1731300000\","
                + "\"type\":\"text\",\"text\":{\"body\":\"1\"},\"context\":{\"from\":\"15550000000\",\"id\":\"wamid.OUT1\"}}]}");

        List<MensajeEntrante> m = parser.mensajes(json);

        assertEquals(List.of(new MensajeEntrante("wamid.IN1", "9511234567", "1", "wamid.OUT1")), m);
    }

    @Test
    void boton_usaPayload() {
        String json = envolver("{\"messages\":[{\"from\":\"529511234567\",\"id\":\"wamid.IN2\",\"type\":\"button\","
                + "\"button\":{\"payload\":\"2\",\"text\":\"Cancelar\"},\"context\":{\"id\":\"wamid.OUT2\"}}]}");

        MensajeEntrante m = parser.mensajes(json).get(0);

        assertEquals("2", m.texto());
        assertEquals("9511234567", m.telefono());
    }

    @Test
    void interactivo_buttonReply() {
        String json = envolver("{\"messages\":[{\"from\":\"529511234567\",\"id\":\"wamid.IN3\",\"type\":\"interactive\","
                + "\"interactive\":{\"type\":\"button_reply\",\"button_reply\":{\"id\":\"1\",\"title\":\"Confirmar\"}}}]}");

        assertEquals("1", parser.mensajes(json).get(0).texto());
    }

    @Test
    void soloEstados_sinMensajes() {
        String json = envolver("{\"statuses\":[{\"id\":\"wamid.OUT1\",\"status\":\"delivered\",\"recipient_id\":\"529511234567\"},"
                + "{\"id\":\"wamid.OUT2\",\"status\":\"read\"}]}");

        assertTrue(parser.mensajes(json).isEmpty());
        assertEquals(List.of(new EstadoEntrega("wamid.OUT1", "ENTREGADO"), new EstadoEntrega("wamid.OUT2", "LEIDO")),
                parser.estados(json));
    }

    @Test
    void tipoNoSoportado_seMarca() {
        String json = envolver("{\"messages\":[{\"from\":\"529511234567\",\"id\":\"wamid.IN4\",\"type\":\"image\",\"image\":{}}]}");
        assertEquals("[image]", parser.mensajes(json).get(0).texto());
    }

    @Test
    void jsonInvalido_400() {
        assertThrows(IllegalArgumentException.class, () -> parser.mensajes("no-json"));
    }
}
