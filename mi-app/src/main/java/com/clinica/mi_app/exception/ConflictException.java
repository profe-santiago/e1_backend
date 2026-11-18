package com.clinica.mi_app.exception;

/** Operación válida pero en conflicto con el estado actual (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
