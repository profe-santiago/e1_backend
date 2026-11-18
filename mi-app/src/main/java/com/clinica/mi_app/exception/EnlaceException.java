package com.clinica.mi_app.exception;

import org.springframework.http.HttpStatus;

/** Error del enlace público de cita, con un código estable para el frontend y un mensaje amable. */
public class EnlaceException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public EnlaceException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
}
