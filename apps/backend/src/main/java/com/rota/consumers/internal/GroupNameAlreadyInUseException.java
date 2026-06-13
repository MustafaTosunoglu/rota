package com.rota.consumers.internal;

public class GroupNameAlreadyInUseException extends RuntimeException {

    public GroupNameAlreadyInUseException(String name) {
        super("A consumer group named '" + name + "' already exists in this organization");
    }
}
