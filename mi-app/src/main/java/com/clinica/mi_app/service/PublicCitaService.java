package com.clinica.mi_app.service;

import com.clinica.mi_app.dto.response.PublicCitaResponse;
import com.clinica.mi_app.exception.EnlaceException;
import com.clinica.mi_app.model.Cita;
import com.clinica.mi_app.model.CitaEnlace;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Página pública /c/{token}: ver la cita y confirmarla o cancelarla con un token de un solo uso. */
@Service
public class PublicCitaService {

    private final CitaEnlaceService enlaceService;
    private final WhatsAppRespuestaService respuestaService;

    public PublicCitaService(CitaEnlaceService enlaceService, WhatsAppRespuestaService respuestaService) {
        this.enlaceService = enlaceService;
        this.respuestaService = respuestaService;
    }

    @Transactional(readOnly = true)
    public PublicCitaResponse ver(String token) {
        return respuesta(validar(token).getCita(), null);
    }

    @Transactional
    public PublicCitaResponse responder(String token, String accion) {
        CitaEnlace enlace = validar(token);
        if (!CitaEnlace.CONFIRMAR.equals(accion) && !CitaEnlace.CANCELAR.equals(accion)) {
            throw new EnlaceException(HttpStatus.BAD_REQUEST, "ACCION_INVALIDA",
                    "No reconocimos la acción. Usa los botones de la página.");
        }
        if (!enlaceService.consumir(enlace, accion)) {
            throw new EnlaceException(HttpStatus.GONE, "USADO",
                    "Ya registramos una respuesta para esta cita. No es necesario que hagas nada más.");
        }
        Cita cita = enlace.getCita();
        if (CitaEnlace.CONFIRMAR.equals(accion)) {
            respuestaService.confirmarDesdeEnlace(cita);
        } else {
            respuestaService.cancelarDesdeEnlace(cita);
        }
        return respuesta(cita, cita.getEstado());
    }

    private CitaEnlace validar(String token) {
        CitaEnlaceService.Resolucion r = enlaceService.resolver(token);
        return switch (r.estado()) {
            case VALIDO -> r.enlace();
            case NO_ENCONTRADO -> throw new EnlaceException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO",
                    "Este enlace no es válido. Revisa que lo hayas abierto completo o comunícate con la clínica.");
            case USADO -> throw new EnlaceException(HttpStatus.GONE, "USADO", mensajeUsado(r.enlace()));
            case REEMPLAZADO -> throw new EnlaceException(HttpStatus.GONE, "REEMPLAZADO",
                    "Este enlace ya no está activo porque te enviamos uno más reciente. Usa el último mensaje que recibiste.");
            case EXPIRADO -> throw new EnlaceException(HttpStatus.GONE, "EXPIRADO",
                    "Este enlace expiró porque ya pasó la hora de la cita. Si necesitas ayuda, comunícate con la clínica.");
            case NO_DISPONIBLE -> throw new EnlaceException(HttpStatus.CONFLICT, "NO_DISPONIBLE",
                    "Esta cita ya no se puede modificar desde el enlace. Si tienes dudas, comunícate con la clínica.");
        };
    }

    private static String mensajeUsado(CitaEnlace enlace) {
        if (CitaEnlace.CONFIRMAR.equals(enlace.getAccion())) {
            return "Ya registramos tu respuesta: confirmaste tu asistencia. ¡Te esperamos!";
        }
        if (CitaEnlace.CANCELAR.equals(enlace.getAccion())) {
            return "Ya registramos tu respuesta: cancelaste esta cita. Si deseas reagendar, comunícate con la clínica.";
        }
        return "Este enlace ya se usó.";
    }

    private static PublicCitaResponse respuesta(Cita c, String resultado) {
        return new PublicCitaResponse(
                primerNombre(c.getPaciente() == null ? null : c.getPaciente().getNombre()),
                c.getOrganizacion() == null ? null : c.getOrganizacion().getNombre(),
                c.getMedico() == null ? null : c.getMedico().getNombre(),
                c.getMedico() == null ? null : c.getMedico().getEspecialidad(),
                c.getConsultorio() == null ? null : c.getConsultorio().getNombre(),
                c.getConsultorio() == null ? null : c.getConsultorio().getDireccion(),
                c.getFechaHora(),
                c.getEstado(),
                resultado,
                c.getDuracionMin(),
                c.getConsultorio() == null ? null : c.getConsultorio().getTelefono());
    }

    /** Solo el primer nombre: la página es pública y no debe exponer el nombre completo. */
    static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        return nombre.trim().split("\\s+")[0];
    }
}
