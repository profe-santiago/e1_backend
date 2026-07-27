package com.clinica.mi_app.security;

import com.clinica.mi_app.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// Genera y valida tokens JWT. El token lleva userId, email, rol y organizacionId
// para que cualquier endpoint pueda saber quién hace la petición sin consultar la BD.
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(Usuario usuario) {
        var builder = Jwts.builder()
                .subject(usuario.getEmail())
                .claim("userId", usuario.getId().toString())
                .claim("rol", usuario.getRol())
                .claim("organizacionId", usuario.getOrganizacion().getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration));

        if (usuario.getMedico() != null) {
            builder.claim("medicoId", usuario.getMedico().getId().toString());
        }

        return builder.signWith(getSignKey()).compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return extractClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
