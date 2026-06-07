package com.rota.iam.internal;

/** Login attempted before the email was verified. Mapped to HTTP 403. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address is not verified");
    }
}
