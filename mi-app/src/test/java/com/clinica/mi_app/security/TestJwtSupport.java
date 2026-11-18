package com.clinica.mi_app.security;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public final class TestJwtSupport {

    private static final KeyPair KEY_PAIR;
    public static final String PUBLIC_KEY_PEM;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();

            byte[] encoded = KEY_PAIR.getPublic().getEncoded();
            PUBLIC_KEY_PEM = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                    + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestJwtSupport() {}

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static RSAPrivateKey privateKey() {
        return (RSAPrivateKey) KEY_PAIR.getPrivate();
    }

    public static String generateToken(UUID userId, String email, String role,
                                        String tenantId, UUID organizacionId) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000));

        if (organizacionId != null) {
            builder.claim("organizacionId", organizacionId.toString());
        }

        return builder.signWith(privateKey()).compact();
    }
}
