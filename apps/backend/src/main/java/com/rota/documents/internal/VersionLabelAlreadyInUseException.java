package com.rota.documents.internal;

public class VersionLabelAlreadyInUseException extends RuntimeException {

    public VersionLabelAlreadyInUseException(String label) {
        super("This document already has a version labelled '" + label + "'");
    }
}
