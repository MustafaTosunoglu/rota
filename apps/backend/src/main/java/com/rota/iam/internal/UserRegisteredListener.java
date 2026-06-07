package com.rota.iam.internal;

import com.rota.iam.api.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On signup, send the email-verification link. {@code UserRegisteredEvent} is published right
 * after the registration transaction commits, so the user row already exists when we issue the
 * token. A failed send is swallowed by {@link VerificationService}/the mailer — it must never
 * undo a successful signup.
 */
@Component
public class UserRegisteredListener {

    private final VerificationService verificationService;

    public UserRegisteredListener(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        verificationService.issueAndSendVerification(event.tenantId(), event.userId(), event.email());
    }
}
