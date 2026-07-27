package com.clinica.mi_app.dto.response;

import java.util.UUID;

public class AuthResponse {

    private String token;
    private UUID userId;
    private String email;
    private String nombre;
    private String rol;
    private UUID organizacionId;
    private UUID medicoId;
    private UUID pacienteId;

    public AuthResponse(String token, UUID userId, String email, String rol, UUID organizacionId) {
        this(token, userId, email, null, rol, organizacionId, null, null);
    }

    public AuthResponse(String token, UUID userId, String email, String rol, UUID organizacionId, UUID medicoId) {
        this(token, userId, email, null, rol, organizacionId, medicoId, null);
    }

    public AuthResponse(String token, UUID userId, String email, String nombre, String rol, UUID organizacionId, UUID medicoId) {
        this(token, userId, email, nombre, rol, organizacionId, medicoId, null);
    }

    public AuthResponse(String token, UUID userId, String email, String nombre, String rol, UUID organizacionId, UUID medicoId, UUID pacienteId) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
        this.organizacionId = organizacionId;
        this.medicoId = medicoId;
        this.pacienteId = pacienteId;
    }

    public String getToken() { return token; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    public UUID getOrganizacionId() { return organizacionId; }
    public UUID getMedicoId() { return medicoId; }
    public UUID getPacienteId() { return pacienteId; }
}
