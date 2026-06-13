package com.rota.importer.internal;

/** Raised when the supplied content cannot be parsed as the chosen format → HTTP 400. */
public class ImportParseException extends RuntimeException {

    public ImportParseException(String message) {
        super(message);
    }

    public ImportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
