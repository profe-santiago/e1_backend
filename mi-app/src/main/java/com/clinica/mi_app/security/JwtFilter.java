package com.clinica.mi_app.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = jwtUtil.validateToken(token);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = claims.getSubject();

        if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = jwtUtil.getRole(claims);

            List<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);

            Map<String, Object> jwtClaims = new HashMap<>();
            jwtClaims.put("userId", UUID.fromString(subject));
            jwtClaims.put("rol", role);

            String tenantId = jwtUtil.getTenantId(claims);
            if (tenantId != null) {
                jwtClaims.put("tenantId", tenantId);
            }

            String email = claims.get("email", String.class);
            if (email != null) {
                jwtClaims.put("email", email);
            }

            String organizacionId = claims.get("organizacionId", String.class);
            if (organizacionId != null) {
                jwtClaims.put("organizacionId", UUID.fromString(organizacionId));
            }

            String medicoId = claims.get("medicoId", String.class);
            if (medicoId != null) {
                jwtClaims.put("medicoId", UUID.fromString(medicoId));
            }

            authToken.setDetails(jwtClaims);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
