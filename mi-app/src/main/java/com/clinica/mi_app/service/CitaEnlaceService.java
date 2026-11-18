package com.clinica.mi_app.service;

import com.clinica.mi_app.config.ClinicaReloj;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.CitaEnlace;
import com.clinica.mi_app.repository.CitaEnlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tokens del canal "enlace": uno nuevo por cada recordatorio enviado, de un solo uso y con
 * vencimiento a la hora de la cita. En la base solo queda el hash SHA-256 del token.
 */
@Service
public class CitaEnlaceService {

    /** Resultado de buscar un token. */
    public enum Estado { VALIDO, NO_ENCONTRADO, USADO, REEMPLAZADO, EXPIRADO, NO_DISPONIBLE }

    public record Resolucion(Estado estado, CitaEnlace enlace) {
        public boolean valido() { return estado == Estado.VALIDO; }
    }

    public record Generado(String token, String url, CitaEnlace enlace) {}

    /** Estados de cita que todavía se pueden confirmar o cancelar desde el enlace. */
    static final Set<String> ESTADOS_RESPONDIBLES = Set.of("SIN_CONFIRMAR", "CONFIRMADA");

    /** 32 bytes en base64url sin relleno = 43 caracteres. */
    private static final Pattern FORMATO_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final CitaEnlaceRepository repo;
    private final ClinicaReloj reloj;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public CitaEnlaceService(CitaEnlaceRepository repo, ClinicaReloj reloj,
                             @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.repo = repo;
        this.reloj = reloj;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
    }

    /** Crea un token nuevo para la cita e invalida los anteriores que no se usaron. */
    @Transactional
    public Generado generar(Cita cita) {
        OffsetDateTime ahora = reloj.ahora();
        List<CitaEnlace> anteriores = repo.findByCitaIdAndUsadoEnIsNull(cita.getId());
        anteriores.forEach(e -> {
            e.setUsadoEn(ahora);
            e.setAccion(CitaEnlace.REEMPLAZADO);
        });
        if (!anteriores.isEmpty()) repo.saveAll(anteriores);

        String token = nuevoToken();
        CitaEnlace enlace = new CitaEnlace();
        enlace.setOrganizacion(cita.getOrganizacion());
        enlace.setCita(cita);
        enlace.setTokenHash(hash(token));
        enlace.setExpiraEn(cita.getFechaHora());
        repo.save(enlace);
        return new Generado(token, url(token), enlace);
    }

    public String url(String token) {
        return publicUrl + "/c/" + token;
    }

    @Transactional(readOnly = true)
    public Resolucion resolver(String token) {
        if (token == null || !FORMATO_TOKEN.matcher(token).matches()) {
            return new Resolucion(Estado.NO_ENCONTRADO, null);
        }
        CitaEnlace enlace = repo.findByTokenHash(hash(token)).orElse(null);
        if (enlace == null || enlace.getCita() == null) {
            return new Resolucion(Estado.NO_ENCONTRADO, null);
        }
        // Aislamiento: el enlace solo vale para una cita de su misma organización
        if (!enlace.getOrganizacion().getId().equals(enlace.getCita().getOrganizacion().getId())) {
            return new Resolucion(Estado.NO_ENCONTRADO, null);
        }
        if (CitaEnlace.REEMPLAZADO.equals(enlace.getAccion())) return new Resolucion(Estado.REEMPLAZADO, enlace);
        if (enlace.getUsadoEn() != null) return new Resolucion(Estado.USADO, enlace);
        if (!reloj.ahora().isBefore(enlace.getExpiraEn())) return new Resolucion(Estado.EXPIRADO, enlace);
        if (!ESTADOS_RESPONDIBLES.contains(enlace.getCita().getEstado())) {
            return new Resolucion(Estado.NO_DISPONIBLE, enlace);
        }
        return new Resolucion(Estado.VALIDO, enlace);
    }

    /** Consume el token de forma atómica. {@code false} si otro request lo usó primero. */
    @Transactional
    public boolean consumir(CitaEnlace enlace, String accion) {
        return repo.consumir(enlace.getId(), accion, reloj.ahora()) == 1;
    }

    String nuevoToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
