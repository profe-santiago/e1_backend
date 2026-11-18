package com.clinica.mi_app.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Emisor de tokens RS256 para desarrollo local (auth.mode=local).
 *
 * Firma tokens con el MISMO formato de claims que emitirá el AuthService de InToGlobe
 * (sub, tenantId, systemId, role) más los claims de contexto que necesita el frontend
 * (organizacionId, medicoId, pacienteId, email). El JwtFilter los valida con la clave
 * pública de siempre (auth.public.key), así que el flujo de seguridad no cambia:
 * al conectar el AuthService real basta con poner auth.mode=intoglobe.
 */
@Component
@ConditionalOnProperty(name = "auth.mode", havingValue = "local")
public class LocalTokenIssuer {

    private final RSAPrivateKey privateKey;
    private final long expirationMs;

    public LocalTokenIssuer(@Value("${auth.local.private-key:}") String privateKeyPem,
                            @Value("${auth.local.private-key-path:}") String privateKeyPath,
                            @Value("${auth.local.expiration-ms:28800000}") long expirationMs) {
        this.privateKey = parsePrivateKey(resolvePem(privateKeyPem, privateKeyPath));
        this.expirationMs = expirationMs;
    }

    public String issue(String subject, String role, Map<String, Object> extraClaims) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("systemId", "clinicas")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs));
        extraClaims.forEach((k, v) -> {
            if (v != null) builder.claim(k, v.toString());
        });
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    private static String resolvePem(String pem, String path) {
        if (pem != null && !pem.isBlank()) return pem;
        if (path != null && !path.isBlank()) {
            try {
                return Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo leer auth.local.private-key-path: " + path, e);
            }
        }
        throw new IllegalStateException("auth.mode=local requiere auth.local.private-key o auth.local.private-key-path");
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo parsear la clave privada RSA (PKCS#8)", e);
        }
    }
}
