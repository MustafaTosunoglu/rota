package com.rota.endpoints.internal;

/** The (method, path) pair must be unique within a document version (§9.3). */
public class DuplicateOperationException extends RuntimeException {

    public DuplicateOperationException(String method, String path) {
        super("This version already documents " + method + " " + path);
    }
}
