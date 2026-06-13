package com.rota.consumers.internal;

/** Same message for unknown / expired / already-used tokens: no information leak. */
public class InvalidInvitationTokenException extends RuntimeException {

    public InvalidInvitationTokenException() {
        super("This invitation link is invalid or has expired");
    }
}
