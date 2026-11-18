package com.clinica.mi_app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.dto.request.PagoRequest;
import com.clinica.mi_app.dto.response.PagoResponse;
import com.clinica.mi_app.exception.ResourceNotFoundException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.model.Pago;
import com.clinica.mi_app.repository.CitaRepository;
import com.clinica.mi_app.repository.OrganizacionRepository;
import com.clinica.mi_app.repository.PagoRepository;

public class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private OrganizacionRepository organizacionRepository;

    @Mock
    private CitaRepository citaRepository;

    /** 16:00Z reales = 10:00 en la clínica → ahora de la clínica = 2026-11-10T10:00Z. */
    @Spy
    private ClinicaReloj clinicaReloj = new ClinicaReloj(
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-10T16:00:00Z"), java.time.ZoneOffset.UTC));

    @InjectMocks
    private PagoService pagoService;

    private UUID organizacionId;
    private UUID citaId;
    private UUID pagoId;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        organizacionId = UUID.randomUUID();
        citaId = UUID.randomUUID();
        pagoId = UUID.randomUUID();
    }

    @Test
    public void testCrearPago_Success() {
        // Arrange
        PagoRequest request = new PagoRequest();
        request.setOrganizacionId(organizacionId);
        request.setCitaId(citaId);
        request.setMonto(new BigDecimal("100.50"));
        request.setMetodo("TARJETA");
        request.setConcepto("Consulta médica");
        request.setReferencia("REF-001");

        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);

        Pago pagoGuardado = new Pago();
        pagoGuardado.setId(pagoId);
        pagoGuardado.setOrganizacion(org);
        pagoGuardado.setCita(cita);
        pagoGuardado.setMonto(request.getMonto());
        pagoGuardado.setMetodo(request.getMetodo());
        pagoGuardado.setConcepto(request.getConcepto());
        pagoGuardado.setReferencia(request.getReferencia());

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoGuardado);

        // Act
        PagoResponse response = pagoService.crear(request);

        // Assert
        assertNotNull(response);
        assertEquals(pagoId, response.getId());
        assertEquals(organizacionId, response.getOrganizacionId());
        assertEquals(citaId, response.getCitaId());
        assertEquals(new BigDecimal("100.50"), response.getMonto());
        verify(pagoRepository, times(1)).save(any(Pago.class));
    }

    @Test
    public void testCrearPago_OrganizacionNotFound() {
        // Arrange
        PagoRequest request = new PagoRequest();
        request.setOrganizacionId(organizacionId);
        request.setCitaId(citaId);
        request.setMonto(new BigDecimal("100.50"));
        request.setMetodo("TARJETA");

        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> pagoService.crear(request));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    public void testBuscarPorId_Success() {
        // Arrange
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);

        Pago pago = new Pago();
        pago.setId(pagoId);
        pago.setOrganizacion(org);
        pago.setCita(cita);
        pago.setMonto(new BigDecimal("100.50"));
        pago.setMetodo("TARJETA");

        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pago));

        // Act
        PagoResponse response = pagoService.buscarPorId(pagoId);

        // Assert
        assertNotNull(response);
        assertEquals(pagoId, response.getId());
        assertEquals(new BigDecimal("100.50"), response.getMonto());
        verify(pagoRepository, times(1)).findById(pagoId);
    }

    @Test
    public void testBuscarPorId_NotFound() {
        // Arrange
        when(pagoRepository.findById(pagoId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> pagoService.buscarPorId(pagoId));
    }

    @Test
    public void testListarPorOrganizacion_Success() {
        // Arrange
        Organizacion org1 = new Organizacion();
        org1.setId(organizacionId);
        Organizacion org2 = new Organizacion();
        org2.setId(organizacionId);

        Cita cita1 = new Cita();
        cita1.setId(citaId);
        Cita cita2 = new Cita();
        cita2.setId(UUID.randomUUID());

        Pago pago1 = new Pago();
        pago1.setId(UUID.randomUUID());
        pago1.setOrganizacion(org1);
        pago1.setCita(cita1);
        pago1.setMonto(new BigDecimal("50.00"));

        Pago pago2 = new Pago();
        pago2.setId(UUID.randomUUID());
        pago2.setOrganizacion(org2);
        pago2.setCita(cita2);
        pago2.setMonto(new BigDecimal("75.00"));

        List<Pago> pagos = List.of(pago1, pago2);

        when(pagoRepository.findByOrganizacionId(organizacionId)).thenReturn(pagos);

        // Act
        List<PagoResponse> responses = pagoService.listarPorOrganizacion(organizacionId);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(pagoRepository, times(1)).findByOrganizacionId(organizacionId);
    }

    @Test
    public void testListarPorEstado_Success() {
        // Arrange
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);

        Pago pago1 = new Pago();
        pago1.setId(UUID.randomUUID());
        pago1.setOrganizacion(org);
        pago1.setCita(cita);
        pago1.setEstado("PAGADO");

        List<Pago> pagos = List.of(pago1);

        when(pagoRepository.findByOrganizacionIdAndEstado(organizacionId, "PAGADO")).thenReturn(pagos);

        // Act
        List<PagoResponse> responses = pagoService.listarPorEstado(organizacionId, "PAGADO");

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        verify(pagoRepository, times(1)).findByOrganizacionIdAndEstado(organizacionId, "PAGADO");
    }

    @Test
    public void testActualizarPago_Success() {
        // Arrange
        PagoRequest request = new PagoRequest();
        request.setMonto(new BigDecimal("120.00"));
        request.setMetodo("EFECTIVO");

        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);
        Pago pagoExistente = new Pago();
        pagoExistente.setId(pagoId);
        pagoExistente.setOrganizacion(org);
        pagoExistente.setCita(cita);

        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(pagoExistente));
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoExistente);

        // Act
        PagoResponse response = pagoService.actualizar(pagoId, request);

        // Assert
        assertNotNull(response);
        verify(pagoRepository, times(1)).save(any(Pago.class));
    }

    // ── Estado PAGADO / pagadoEn y pagos por cita ──────────────────────────────────

    private static final java.time.OffsetDateTime AHORA_CLINICA = java.time.OffsetDateTime.parse("2026-11-10T10:00:00Z");

    private PagoRequest requestBase(String estado) {
        PagoRequest request = new PagoRequest();
        request.setOrganizacionId(organizacionId);
        request.setCitaId(citaId);
        request.setMonto(new BigDecimal("650.00"));
        request.setMetodo("EFECTIVO");
        request.setEstado(estado);
        return request;
    }

    private void prepararCrear() {
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);
        when(organizacionRepository.findById(organizacionId)).thenReturn(Optional.of(org));
        when(citaRepository.findById(citaId)).thenReturn(Optional.of(cita));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pago pagoExistente(String estado, java.time.OffsetDateTime pagadoEn) {
        Organizacion org = new Organizacion();
        org.setId(organizacionId);
        Cita cita = new Cita();
        cita.setId(citaId);
        Pago p = new Pago();
        p.setId(pagoId);
        p.setOrganizacion(org);
        p.setCita(cita);
        p.setMonto(new BigDecimal("650.00"));
        p.setMetodo("EFECTIVO");
        p.setEstado(estado);
        p.setPagadoEn(pagadoEn);
        when(pagoRepository.findById(pagoId)).thenReturn(Optional.of(p));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        return p;
    }

    @Test
    public void testCrear_conEstadoPagado_llenaPagadoEnConHoraDeLaClinica() {
        prepararCrear();

        PagoResponse r = pagoService.crear(requestBase("PAGADO"));

        assertEquals("PAGADO", r.getEstado());
        assertEquals(AHORA_CLINICA, r.getPagadoEn());
    }

    @Test
    public void testCrear_sinEstado_quedaPendienteSinPagadoEn() {
        prepararCrear();

        PagoResponse r = pagoService.crear(requestBase(null));

        assertEquals("PENDIENTE", r.getEstado());
        org.junit.jupiter.api.Assertions.assertNull(r.getPagadoEn());
    }

    @Test
    public void testActualizar_aPagadoSinPagadoEn_loLlena() {
        pagoExistente("PENDIENTE", null);

        PagoResponse r = pagoService.actualizar(pagoId, requestBase("PAGADO"));

        assertEquals("PAGADO", r.getEstado());
        assertEquals(AHORA_CLINICA, r.getPagadoEn());
    }

    @Test
    public void testActualizar_aPagadoConPagadoEnExistente_loConserva() {
        java.time.OffsetDateTime previo = java.time.OffsetDateTime.parse("2026-11-01T12:35:00Z");
        pagoExistente("PAGADO", previo);

        PagoResponse r = pagoService.actualizar(pagoId, requestBase("PAGADO"));

        assertEquals(previo, r.getPagadoEn());
    }

    @Test
    public void testActualizar_aPendiente_limpiaPagadoEn() {
        pagoExistente("PAGADO", AHORA_CLINICA);

        PagoResponse r = pagoService.actualizar(pagoId, requestBase("PENDIENTE"));

        assertEquals("PENDIENTE", r.getEstado());
        org.junit.jupiter.api.Assertions.assertNull(r.getPagadoEn());
    }

    @Test
    public void testActualizar_sinEstado_conservaEstadoYPagadoEn() {
        pagoExistente("PAGADO", AHORA_CLINICA);

        PagoResponse r = pagoService.actualizar(pagoId, requestBase(null));

        assertEquals("PAGADO", r.getEstado());
        assertEquals(AHORA_CLINICA, r.getPagadoEn());
    }

    @Test
    public void testListarPorCita_sinPagos_devuelveListaVacia() {
        when(pagoRepository.findByCitaId(citaId)).thenReturn(List.of());

        assertEquals(List.of(), pagoService.listarPorCita(citaId));
    }

    @Test
    public void testListarPorCita_conPago_devuelveUnElemento() {
        Pago p = pagoExistente("PAGADO", AHORA_CLINICA);
        when(pagoRepository.findByCitaId(citaId)).thenReturn(List.of(p));

        List<PagoResponse> r = pagoService.listarPorCita(citaId);

        assertEquals(1, r.size());
        assertEquals(pagoId, r.get(0).getId());
    }
}
