package com.clinica.mi_app.whatsapp;

import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Consultorio;
import com.clinica.mi_app.model.Medico;
import com.clinica.mi_app.model.Paciente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelefonoYPlantillaTest {

    @ParameterizedTest
    @CsvSource({
            "9511234567, 9511234567",
            "'951 123 4567', 9511234567",
            "+529511234567, 9511234567",
            "529511234567, 9511234567",
            "+5219511234567, 9511234567",
            "whatsapp:+5219511234567, 9511234567",
            "whatsapp:+529511234567, 9511234567"
    })
    void aDiezDigitos_formatosMx(String entrada, String esperado) {
        assertEquals(esperado, TelefonoMx.aDiezDigitos(entrada));
    }

    @Test
    void aDiezDigitos_invalidos() {
        assertNull(TelefonoMx.aDiezDigitos(null));
        assertNull(TelefonoMx.aDiezDigitos("12345"));
        assertNull(TelefonoMx.aDiezDigitos("+1 415 523 8886 0"));
    }

    @Test
    void formatosDeSalida() {
        assertEquals("529511234567", TelefonoMx.aInternacional("951-123-4567"));
        assertEquals("+529511234567", TelefonoMx.aE164("5219511234567"));
        assertNull(TelefonoMx.aE164("123"));
    }

    @Test
    void plantilla_recordatorioEnEspanolConHoraDeRelojEnUtc() {
        Paciente p = new Paciente();
        p.setNombre("Ana Ramírez");
        Medico m = new Medico();
        m.setNombre("Dra. Mariana López");
        Consultorio c = new Consultorio();
        c.setNombre("Consultorio Centro");
        Cita cita = new Cita();
        cita.setPaciente(p);
        cita.setMedico(m);
        cita.setConsultorio(c);
        cita.setFechaHora(OffsetDateTime.parse("2026-11-11T09:00:00Z"));

        List<String> params = RecordatorioPlantilla.parametros(cita);

        assertEquals(List.of("Ana Ramírez", "Dra. Mariana López", "miércoles 11 de noviembre", "9:00 a.m.", "Consultorio Centro"), params);
        assertEquals("Hola Ana Ramírez, te recordamos tu cita con Dra. Mariana López el miércoles 11 de noviembre"
                        + " a las 9:00 a.m. en Consultorio Centro. Responde 1 para confirmar o 2 para cancelar.",
                RecordatorioPlantilla.renderizar(params));
    }

    @Test
    void plantilla_horasDeTarde() {
        assertEquals("12:30 p.m.", RecordatorioPlantilla.hora(OffsetDateTime.parse("2026-11-11T12:30:00Z")));
        assertEquals("4:05 p.m.", RecordatorioPlantilla.hora(OffsetDateTime.parse("2026-11-11T16:05:00Z")));
        assertEquals("12:00 a.m.", RecordatorioPlantilla.hora(OffsetDateTime.parse("2026-11-11T00:00:00Z")));
    }
}
