package com.rota.consumers.internal;

/** Generic 404 for consumers-module resources (group, member, access grant). */
public class ConsumerNotFoundException extends RuntimeException {

    public ConsumerNotFoundException(String what, Object id) {
        super(what + " not found: " + id);
    }
}
