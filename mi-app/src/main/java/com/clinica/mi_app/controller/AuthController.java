package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.LoginRequest;
import com.clinica.mi_app.dto.request.RegistroRequest;
import com.clinica.mi_app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacion", description = "Proxy hacia el AuthService externo de InToGlobe")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Proxy: autentica contra el AuthService externo y retorna token RS256")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso, retorna token JWT RS256"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos en la solicitud"),
        @ApiResponse(responseCode = "401", description = "Email o contrasena incorrectos")
    })
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String[] tenant = parseTenant(request.getHeader("Host"));
        return authService.login(req.getEmail(), req.getPassword(), tenant[0], tenant[1]);
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar usuario", description = "Proxy: registra en el AuthService externo y retorna token RS256")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o email ya existe")
    })
    public ResponseEntity<String> registro(@Valid @RequestBody RegistroRequest req, HttpServletRequest request) {
        String[] tenant = parseTenant(request.getHeader("Host"));
        return authService.registro(req.getEmail(), req.getPassword(), tenant[0], tenant[1]);
    }

    // Formato esperado: {tenantSlug}.med.intoglobe.com.mx → ["eldoc", "clinicas"]
    // En desarrollo local (localhost:8080) usa defaults.
    private String[] parseTenant(String host) {
        if (host != null && host.contains(".intoglobe.com.mx")) {
            String subdomain = host.split("\\.")[0];
            return new String[]{subdomain, "clinicas"};
        }
        return new String[]{"default", "clinicas"};
    }
}
