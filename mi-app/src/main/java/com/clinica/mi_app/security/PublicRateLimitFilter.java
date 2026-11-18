package com.clinica.mi_app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite básico por IP para la página pública /api/public/citas/** (ventana fija de 1 minuto,
 * en memoria). Suficiente para la demo; con varias instancias haría falta un almacén compartido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    static final String PREFIJO = "/api/public/citas/";
    private static final long VENTANA_MS = 60_000;
    private static final int MAX_IPS = 10_000;

    private final int maxPorMinuto;
    private final Clock clock;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    private record Ventana(long inicio, int cuenta) {}

    @Autowired
    public PublicRateLimitFilter(@Value("${public.rate-limit.por-minuto:20}") int maxPorMinuto) {
        this(maxPorMinuto, Clock.systemUTC());
    }

    PublicRateLimitFilter(int maxPorMinuto, Clock clock) {
        this.maxPorMinuto = maxPorMinuto;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIJO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long ahora = clock.millis();
        if (ventanas.size() > MAX_IPS) {
            ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= VENTANA_MS);
        }
        Ventana v = ventanas.compute(ipCliente(request), (ip, actual) ->
                actual == null || ahora - actual.inicio() >= VENTANA_MS
                        ? new Ventana(ahora, 1)
                        : new Ventana(actual.inicio(), actual.cuenta() + 1));

        if (v.cuenta() > maxPorMinuto) {
            long esperaSeg = Math.max(1, (VENTANA_MS - (ahora - v.inicio()) + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(esperaSeg));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":429,\"codigo\":\"DEMASIADOS_INTENTOS\","
                    + "\"mensaje\":\"Demasiados intentos. Espera un minuto y vuelve a intentarlo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * IP del cliente. Detrás del proxy de Vite o de cloudflared todas las peticiones llegan desde la
     * misma IP local, por eso se miran primero las cabeceras del proxy.
     *
     * TODO: CF-Connecting-IP y X-Forwarded-For las puede inventar cualquier cliente; solo deberían
     * confiarse cuando la petición viene de un proxy conocido (cloudflared / Vite / balanceador).
     * Para producción, validar request.getRemoteAddr() contra una lista de proxies de confianza.
     */
    static String ipCliente(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
