package com.clinica.mi_app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtUtil {

    @Value("${auth.public.key}")
    private String publicKeyPem;

    private RSAPublicKey publicKey;

    @PostConstruct
    private void init() {
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getTenantId(Claims claims) { return claims.get("tenantId", String.class); }
    public String getSystemId(Claims claims) { return claims.get("systemId", String.class); }
    public String getRole(Claims claims)     { return claims.get("role", String.class); }
    public String getUserId(Claims claims)   { return claims.getSubject(); }

    private RSAPublicKey parsePublicKey(String pem) {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo parsear la clave publica RSA", e);
        }
    }
}
