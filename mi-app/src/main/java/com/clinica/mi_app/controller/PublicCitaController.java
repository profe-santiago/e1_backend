package com.clinica.mi_app.controller;

import com.clinica.mi_app.dto.request.PublicRespuestaRequest;
import com.clinica.mi_app.dto.response.PublicCitaResponse;
import com.clinica.mi_app.dto.response.PublicErrorResponse;
import com.clinica.mi_app.exception.EnlaceException;
import com.clinica.mi_app.service.PublicCitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * Página pública del canal "enlace". Es la ÚNICA ruta de /api/public sin JWT (ver SecurityConfig):
 * el token de un solo uso es la credencial. Limitada por IP con PublicRateLimitFilter.
 */
@RestController
@RequestMapping("/api/public/citas")
@Tag(name = "Cita pública", description = "Confirmar o cancelar una cita desde el enlace del recordatorio (sin login)")
public class PublicCitaController {

    private final PublicCitaService service;

    public PublicCitaController(PublicCitaService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Ver la cita del enlace")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Enlace válido"),
        @ApiResponse(responseCode = "404", description = "Enlace inválido"),
        @ApiResponse(responseCode = "409", description = "La cita ya no se puede modificar"),
        @ApiResponse(responseCode = "410", description = "Enlace usado, reemplazado o expirado"),
        @ApiResponse(responseCode = "429", description = "Demasiadas peticiones desde la misma IP")
    })
    public PublicCitaResponse ver(@PathVariable String token) {
        return service.ver(token);
    }

    @PostMapping("/{token}")
    @Operation(summary = "Confirmar o cancelar la cita (consume el enlace)")
    public PublicCitaResponse responder(@PathVariable String token, @Valid @RequestBody PublicRespuestaRequest req) {
        return service.responder(token, req.accion());
    }

    @ExceptionHandler(EnlaceException.class)
    public ResponseEntity<PublicErrorResponse> enlace(EnlaceException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new PublicErrorResponse(ex.getStatus().value(), ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PublicErrorResponse> invalida(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new PublicErrorResponse(HttpStatus.BAD_REQUEST.value(),
                "ACCION_INVALIDA", "No reconocimos la acción. Usa los botones de la página."));
    }
}
