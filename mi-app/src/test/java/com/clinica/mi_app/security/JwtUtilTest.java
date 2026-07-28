package com.clinica.mi_app.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    public void setup() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "publicKeyPem", TestJwtSupport.PUBLIC_KEY_PEM);
        ReflectionTestUtils.invokeMethod(jwtUtil, "init");
    }

    @Test
    public void testValidateToken_Success() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = TestJwtSupport.generateToken(userId, "test@clinic.com", "MEDICO", "eldoc", orgId);

        Claims claims = jwtUtil.validateToken(token);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("test@clinic.com", claims.get("email", String.class));
    }

    @Test
    public void testValidateToken_InvalidToken() {
        assertThrows(Exception.class, () -> jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    public void testGetTenantId() {
        UUID userId = UUID.randomUUID();
        String token = TestJwtSupport.generateToken(userId, "test@clinic.com", "ADMIN", "eldoc", null);

        Claims claims = jwtUtil.validateToken(token);

        assertEquals("eldoc", jwtUtil.getTenantId(claims));
    }

    @Test
    public void testGetRole() {
        UUID userId = UUID.randomUUID();
        String token = TestJwtSupport.generateToken(userId, "test@clinic.com", "PACIENTE", "eldoc", null);

        Claims claims = jwtUtil.validateToken(token);

        assertEquals("PACIENTE", jwtUtil.getRole(claims));
    }

    @Test
    public void testGetUserId() {
        UUID userId = UUID.randomUUID();
        String token = TestJwtSupport.generateToken(userId, "test@clinic.com", "MEDICO", "eldoc", null);

        Claims claims = jwtUtil.validateToken(token);

        assertEquals(userId.toString(), jwtUtil.getUserId(claims));
    }
}
