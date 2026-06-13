package com.rota.endpoints.internal;

/** Generic 404 for endpoints-module resources (category, endpoint, parameter, body, response). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " not found: " + id);
    }
}
