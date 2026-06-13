package com.rota.documents.internal;

public class SlugAlreadyInUseException extends RuntimeException {

    public SlugAlreadyInUseException(String slug) {
        super("A document with slug '" + slug + "' already exists in this organization");
    }
}
