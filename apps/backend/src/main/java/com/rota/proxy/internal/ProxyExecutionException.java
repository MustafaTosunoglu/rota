package com.rota.proxy.internal;

/** Raised when the target API cannot be reached (timeout, connection refused, …) → HTTP 502. */
public class ProxyExecutionException extends RuntimeException {

    public ProxyExecutionException(String message) {
        super(message);
    }
}
