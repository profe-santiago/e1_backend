package com.clinica.mi_app.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticatedUserTest {

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Map<String, Object> claims) {
        var auth = new UsernamePasswordAuthenticationToken("test-subject", null, List.of());
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void testGetTenantId() {
        authenticate(Map.of(
                "userId", UUID.randomUUID(),
                "rol", "ADMIN",
                "tenantId", "eldoc"
        ));

        assertEquals("eldoc", AuthenticatedUser.getTenantId());
    }

    @Test
    public void testGetTenantId_Null() {
        authenticate(Map.of(
                "userId", UUID.randomUUID(),
                "rol", "PACIENTE"
        ));

        assertNull(AuthenticatedUser.getTenantId());
    }

    @Test
    public void testGetUserId() {
        UUID userId = UUID.randomUUID();
        authenticate(Map.of("userId", userId, "rol", "MEDICO"));

        assertEquals(userId, AuthenticatedUser.getUserId());
    }

    @Test
    public void testGetRol() {
        authenticate(Map.of("userId", UUID.randomUUID(), "rol", "MEDICO"));

        assertEquals("MEDICO", AuthenticatedUser.getRol());
    }

    @Test
    public void testGetEmail() {
        authenticate(Map.of("userId", UUID.randomUUID(), "rol", "ADMIN", "email", "admin@clinic.com"));

        assertEquals("admin@clinic.com", AuthenticatedUser.getEmail());
    }
}
