package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.response.PublicCitaResponse;
import com.clinica.mi_app.exception.EnlaceException;
import com.clinica.mi_app.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicCitaServiceTest {

    @Mock private CitaEnlaceService enlaceService;
    @Mock private WhatsAppRespuestaService respuestaService;

    private PublicCitaService service;
    private Cita cita;
    private CitaEnlace enlace;
    private static final String TOKEN = "token-de-prueba";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new PublicCitaService(enlaceService, respuestaService);
        cita = cita();
        enlace = new CitaEnlace();
        enlace.setId(UUID.randomUUID());
        enlace.setCita(cita);
        enlace.setOrganizacion(cita.getOrganizacion());
    }

    @Test
    void ver_valido_soloDatosMinimos() {
        resolverA(CitaEnlaceService.Estado.VALIDO);

        PublicCitaResponse r = service.ver(TOKEN);

        assertEquals("Ana", r.pacienteNombre(), "solo el primer nombre");
        assertEquals("Clínica Centro", r.clinica());
        assertEquals("Dra. Mariana López Ruiz", r.medico());
        assertEquals("Pediatría", r.especialidad());
        assertEquals("Consultorio 1", r.consultorio());
        assertEquals(OffsetDateTime.parse("2026-11-19T10:00:00Z"), r.fechaHora());
        assertEquals("SIN_CONFIRMAR", r.estado());
        assertNull(r.resultado());
        assertEquals((short) 30, r.duracionMin());
        assertEquals("9515550100", r.telefonoClinica(), "teléfono público del consultorio");
        assertFalse(r.toString().contains("9511234567"), "nunca expone el teléfono");
        verifyNoInteractions(respuestaService);
    }

    @Test
    void responder_confirmar_consumeYConfirma() {
        resolverA(CitaEnlaceService.Estado.VALIDO);
        when(enlaceService.consumir(enlace, "CONFIRMAR")).thenReturn(true);
        doAnswer(inv -> {
            cita.setEstado("CONFIRMADA");
            return null;
        }).when(respuestaService).confirmarDesdeEnlace(cita);

        PublicCitaResponse r = service.responder(TOKEN, "CONFIRMAR");

        verify(respuestaService).confirmarDesdeEnlace(cita);
        verify(respuestaService, never()).cancelarDesdeEnlace(any());
        assertEquals("CONFIRMADA", r.resultado());
    }

    @Test
    void responder_cancelar_consumeYCancela() {
        resolverA(CitaEnlaceService.Estado.VALIDO);
        when(enlaceService.consumir(enlace, "CANCELAR")).thenReturn(true);
        doAnswer(inv -> {
            cita.setEstado("CANCELADA");
            return null;
        }).when(respuestaService).cancelarDesdeEnlace(cita);

        PublicCitaResponse r = service.responder(TOKEN, "CANCELAR");

        verify(respuestaService).cancelarDesdeEnlace(cita);
        verify(respuestaService, never()).confirmarDesdeEnlace(any());
        assertEquals("CANCELADA", r.resultado());
    }

    @Test
    void responder_otroRequestLoUsoPrimero_410_sinTocarLaCita() {
        resolverA(CitaEnlaceService.Estado.VALIDO);
        when(enlaceService.consumir(enlace, "CANCELAR")).thenReturn(false);

        EnlaceException ex = assertThrows(EnlaceException.class, () -> service.responder(TOKEN, "CANCELAR"));

        assertEquals(HttpStatus.GONE, ex.getStatus());
        assertEquals("USADO", ex.getCodigo());
        verifyNoInteractions(respuestaService);
    }

    @Test
    void responder_accionInvalida_400_sinConsumir() {
        resolverA(CitaEnlaceService.Estado.VALIDO);

        EnlaceException ex = assertThrows(EnlaceException.class, () -> service.responder(TOKEN, "BORRAR"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(enlaceService, never()).consumir(any(), any());
    }

    @Test
    void tokenUsado_410_conMensajeDeLoQueRespondio() {
        enlace.setAccion(CitaEnlace.CONFIRMAR);
        resolverA(CitaEnlaceService.Estado.USADO);

        EnlaceException ex = assertThrows(EnlaceException.class, () -> service.ver(TOKEN));

        assertEquals(HttpStatus.GONE, ex.getStatus());
        assertEquals("USADO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("confirmaste"));
    }

    @Test
    void cadaEstadoNoValido_tieneSuCodigoYStatus_yNuncaModificaLaCita() {
        esperar(CitaEnlaceService.Estado.NO_ENCONTRADO, HttpStatus.NOT_FOUND);
        esperar(CitaEnlaceService.Estado.EXPIRADO, HttpStatus.GONE);
        esperar(CitaEnlaceService.Estado.REEMPLAZADO, HttpStatus.GONE);
        esperar(CitaEnlaceService.Estado.NO_DISPONIBLE, HttpStatus.CONFLICT);
        verifyNoInteractions(respuestaService);
        verify(enlaceService, never()).consumir(any(), any());
    }

    @Test
    void primerNombre() {
        assertEquals("Ana", PublicCitaService.primerNombre("  Ana Sofía Ramírez Mendoza "));
        assertNull(PublicCitaService.primerNombre(" "));
    }

    private void esperar(CitaEnlaceService.Estado estado, HttpStatus status) {
        resolverA(estado);
        EnlaceException ver = assertThrows(EnlaceException.class, () -> service.ver(TOKEN));
        EnlaceException responder = assertThrows(EnlaceException.class, () -> service.responder(TOKEN, "CANCELAR"));
        assertEquals(status, ver.getStatus());
        assertEquals(estado.name(), ver.getCodigo());
        assertEquals(status, responder.getStatus());
        assertFalse(ver.getMessage().isBlank());
    }

    private void resolverA(CitaEnlaceService.Estado estado) {
        when(enlaceService.resolver(TOKEN)).thenReturn(new CitaEnlaceService.Resolucion(
                estado, estado == CitaEnlaceService.Estado.NO_ENCONTRADO ? null : enlace));
    }

    private static Cita cita() {
        Organizacion o = new Organizacion();
        o.setId(UUID.randomUUID());
        o.setNombre("Clínica Centro");
        Paciente p = new Paciente();
        p.setNombre("Ana Sofía Ramírez Mendoza");
        p.setTelefono("9511234567");
        Medico m = new Medico();
        m.setNombre("Dra. Mariana López Ruiz");
        m.setEspecialidad("Pediatría");
        Consultorio cons = new Consultorio();
        cons.setNombre("Consultorio 1");
        cons.setTelefono("9515550100");
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(o);
        c.setPaciente(p);
        c.setMedico(m);
        c.setConsultorio(cons);
        c.setEstado("SIN_CONFIRMAR");
        c.setDuracionMin((short) 30);
        c.setFechaHora(OffsetDateTime.parse("2026-11-19T10:00:00Z"));
        return c;
    }
}
