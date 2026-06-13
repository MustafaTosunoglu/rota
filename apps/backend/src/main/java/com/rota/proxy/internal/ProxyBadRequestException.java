package com.rota.proxy.internal;

/** Raised for a malformed Try It request (e.g. environment not in the endpoint's version) → 400. */
public class ProxyBadRequestException extends RuntimeException {

    public ProxyBadRequestException(String message) {
        super(message);
    }
}
