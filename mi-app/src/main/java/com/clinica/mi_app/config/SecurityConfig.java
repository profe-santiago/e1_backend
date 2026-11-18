package com.clinica.mi_app.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.clinica.mi_app.exception.ErrorResponse;
import com.clinica.mi_app.security.JwtFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    /** URL pública del frontend (canal "enlace"): el proxy de Vite reenvía ese Origin desde el celular. */
    @Value("${app.public-url:}")
    private String publicUrl;

    /** Orígenes extra separados por comas (APP_CORS_EXTRA_ORIGINS), p. ej. el dominio del túnel. */
    @Value("${app.cors.extra-origins:}")
    private String extraOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new JavaTimeModule());

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            jsonMapper.writeValue(response.getOutputStream(),
                                    new ErrorResponse(401, "Autenticación requerida"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            jsonMapper.writeValue(response.getOutputStream(),
                                    new ErrorResponse(403, "Acceso denegado: no tienes permisos"));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                // Webhooks de WhatsApp: se autentican con la firma del proveedor.
                                // El resto de /api/whatsapp/** (conversaciones, simulador) exige JWT.
                                "/api/whatsapp/twilio",
                                "/api/whatsapp/twilio/status",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Canal "enlace": SOLO la página pública de una cita por token (un segmento).
                        // Cualquier otra ruta bajo /api/public sigue exigiendo JWT.
                        .requestMatchers(HttpMethod.GET, "/api/public/citas/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/citas/*").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Usado por UsuarioService para hashear contraseñas de usuarios creados desde el panel
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos(allowedOrigins, publicUrl, extraOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Lista exacta de orígenes: base (cors.allowed-origins) + app.public-url + extras. Sin barra final,
     * sin vacíos, sin duplicados y nunca "*" (incompatible con allowCredentials).
     */
    static List<String> origenesPermitidos(String... listas) {
        java.util.LinkedHashSet<String> origenes = new java.util.LinkedHashSet<>();
        for (String lista : listas) {
            if (lista == null) continue;
            for (String o : lista.split(",")) {
                String limpio = o.trim().replaceAll("/+$", "");
                if (!limpio.isEmpty() && !limpio.contains("*")) origenes.add(limpio);
            }
        }
        return List.copyOf(origenes);
    }
}
