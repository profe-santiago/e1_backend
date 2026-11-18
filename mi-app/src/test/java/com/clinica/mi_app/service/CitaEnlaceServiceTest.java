package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.CitaEnlace;
import com.clinica.mi_app.model.Organizacion;
import com.clinica.mi_app.repository.CitaEnlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CitaEnlaceServiceTest {

    @Mock private CitaEnlaceRepository repo;

    /** 16:00Z reales = 10:00 en la clínica → ahora = 2026-11-10T10:00Z (convención de reloj en UTC). */
    private final ClinicaReloj reloj = new ClinicaReloj(Clock.fixed(Instant.parse("2026-11-10T16:00:00Z"), ZoneOffset.UTC));
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-10T10:00:00Z");

    private CitaEnlaceService service;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new CitaEnlaceService(repo, reloj, "http://192.168.0.10:5173/");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByCitaIdAndUsadoEnIsNull(any())).thenReturn(List.of());
    }

    // ── Generación ──

    @Test
    void generar_tokenAleatorioUrlSafe_soloGuardaElHash_yExpiraALaHoraDeLaCita() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);

        CitaEnlaceService.Generado g = service.generar(cita);

        assertTrue(g.token().matches("[A-Za-z0-9_-]{43}"), "32 bytes en base64url");
        assertEquals("http://192.168.0.10:5173/c/" + g.token(), g.url(), "sin doble barra");

        ArgumentCaptor<CitaEnlace> captor = ArgumentCaptor.forClass(CitaEnlace.class);
        verify(repo).save(captor.capture());
        CitaEnlace guardado = captor.getValue();
        assertEquals(CitaEnlaceService.hash(g.token()), guardado.getTokenHash());
        assertEquals(64, guardado.getTokenHash().length());
        assertFalse(guardado.getTokenHash().contains(g.token()), "nunca se guarda el token en claro");
        assertEquals(cita.getFechaHora(), guardado.getExpiraEn());
        assertSame(cita, guardado.getCita());
        assertSame(cita.getOrganizacion(), guardado.getOrganizacion());
        assertNull(guardado.getUsadoEn());
    }

    @Test
    void generar_cadaVezUnTokenDistinto() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);
        assertNotEquals(service.generar(cita).token(), service.generar(cita).token());
    }

    @Test
    void generar_invalidaLosEnlacesAnterioresSinUsar() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);
        CitaEnlace anterior = enlace(cita, null, null);
        when(repo.findByCitaIdAndUsadoEnIsNull(cita.getId())).thenReturn(List.of(anterior));

        service.generar(cita);

        assertEquals(CitaEnlace.REEMPLAZADO, anterior.getAccion());
        assertEquals(AHORA, anterior.getUsadoEn());
        verify(repo).saveAll(List.of(anterior));
    }

    // ── Resolución ──

    @Test
    void resolver_valido() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);
        CitaEnlace e = enlace(cita, null, null);
        when(repo.findByTokenHash(CitaEnlaceService.hash(TOKEN))).thenReturn(Optional.of(e));

        CitaEnlaceService.Resolucion r = service.resolver(TOKEN);

        assertTrue(r.valido());
        assertSame(e, r.enlace());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corto", "con espacios y simbolos!!!!!!!!!!!!!!!!!!!!!!!!!", "../../api/citas"})
    void resolver_formatoInvalido_noEncontrado_sinConsultarLaBase(String token) {
        assertEquals(CitaEnlaceService.Estado.NO_ENCONTRADO, service.resolver(token).estado());
        verify(repo, never()).findByTokenHash(anyString());
    }

    @Test
    void resolver_tokenDesconocido_noEncontrado() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertEquals(CitaEnlaceService.Estado.NO_ENCONTRADO, service.resolver(TOKEN).estado());
    }

    @Test
    void resolver_usado_unSoloUso() {
        Cita cita = cita("CONFIRMADA", "2026-11-11T10:00:00Z", orgId);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(enlace(cita, AHORA.minusHours(1), CitaEnlace.CONFIRMAR)));

        CitaEnlaceService.Resolucion r = service.resolver(TOKEN);

        assertEquals(CitaEnlaceService.Estado.USADO, r.estado());
        assertEquals(CitaEnlace.CONFIRMAR, r.enlace().getAccion());
    }

    @Test
    void resolver_reemplazadoPorUnoMasReciente() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(enlace(cita, AHORA, CitaEnlace.REEMPLAZADO)));
        assertEquals(CitaEnlaceService.Estado.REEMPLAZADO, service.resolver(TOKEN).estado());
    }

    @Test
    void resolver_expiraALaHoraDeLaCita_segunElRelojDeLaClinica() {
        // La cita es a las 10:00 (reloj de la clínica) y ahora son las 10:00 → ya expiró
        Cita enPunto = cita("SIN_CONFIRMAR", "2026-11-10T10:00:00Z", orgId);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(enlace(enPunto, null, null)));
        assertEquals(CitaEnlaceService.Estado.EXPIRADO, service.resolver(TOKEN).estado());

        // 10:01 todavía no: aunque en UTC "real" ya serían las 16:00
        Cita unMinutoDespues = cita("SIN_CONFIRMAR", "2026-11-10T10:01:00Z", orgId);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(enlace(unMinutoDespues, null, null)));
        assertTrue(service.resolver(TOKEN).valido());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADA", "NO_ASISTIO", "REAGENDADA"})
    void resolver_citaQueYaNoSePuedeModificar_noDisponible(String estado) {
        Cita cita = cita(estado, "2026-11-11T10:00:00Z", orgId);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(enlace(cita, null, null)));
        assertEquals(CitaEnlaceService.Estado.NO_DISPONIBLE, service.resolver(TOKEN).estado());
    }

    @Test
    void resolver_enlaceDeOtraOrganizacionQueLaCita_noEncontrado() {
        Cita cita = cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId);
        CitaEnlace e = enlace(cita, null, null);
        Organizacion otra = new Organizacion();
        otra.setId(UUID.randomUUID());
        e.setOrganizacion(otra);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(e));

        assertEquals(CitaEnlaceService.Estado.NO_ENCONTRADO, service.resolver(TOKEN).estado());
    }

    // ── Consumo ──

    @Test
    void consumir_atomico_soloLaPrimeraVez() {
        CitaEnlace e = enlace(cita("SIN_CONFIRMAR", "2026-11-11T10:00:00Z", orgId), null, null);
        when(repo.consumir(e.getId(), CitaEnlace.CANCELAR, AHORA)).thenReturn(1).thenReturn(0);

        assertTrue(service.consumir(e, CitaEnlace.CANCELAR));
        assertFalse(service.consumir(e, CitaEnlace.CANCELAR));
        verify(repo, times(2)).consumir(eq(e.getId()), eq(CitaEnlace.CANCELAR), eq(AHORA));
    }

    // ── Helpers ──

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";

    private static Cita cita(String estado, String fechaHora, UUID org) {
        Organizacion o = new Organizacion();
        o.setId(org);
        Cita c = new Cita();
        c.setId(UUID.randomUUID());
        c.setOrganizacion(o);
        c.setEstado(estado);
        c.setFechaHora(OffsetDateTime.parse(fechaHora));
        return c;
    }

    private static CitaEnlace enlace(Cita cita, OffsetDateTime usadoEn, String accion) {
        CitaEnlace e = new CitaEnlace();
        e.setId(UUID.randomUUID());
        e.setCita(cita);
        e.setOrganizacion(cita.getOrganizacion());
        e.setExpiraEn(cita.getFechaHora());
        e.setUsadoEn(usadoEn);
        e.setAccion(accion);
        return e;
    }
}
