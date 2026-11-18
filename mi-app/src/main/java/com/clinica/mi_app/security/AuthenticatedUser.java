package com.clinica.mi_app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;

public class AuthenticatedUser {

    private AuthenticatedUser() {}

    public static UUID getUserId() {
        return (UUID) claims().get("userId");
    }

    public static String getRol() {
        return (String) claims().get("rol");
    }

    public static UUID getOrganizacionId() {
        return (UUID) claims().get("organizacionId");
    }

    public static UUID getMedicoId() {
        return (UUID) claims().get("medicoId");
    }

    public static String getEmail() {
        return (String) claims().get("email");
    }

    public static String getTenantId() {
        return (String) claims().get("tenantId");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> claims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Map<String, Object>) auth.getDetails();
    }
}
