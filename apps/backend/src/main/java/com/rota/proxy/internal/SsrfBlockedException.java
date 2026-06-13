package com.rota.proxy.internal;

/** Raised when a target URL is not allowed (private IP, bad scheme, unresolvable) → HTTP 400. */
public class SsrfBlockedException extends RuntimeException {

    public SsrfBlockedException(String message) {
        super(message);
    }
}
