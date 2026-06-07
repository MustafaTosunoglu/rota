package com.rota.common.email;

/**
 * Sends transactional email. Dev uses SMTP → maildev ({@link SmtpEmailSender}); production
 * will swap in Resend (Phase 1F). Tests provide a recording implementation.
 */
public interface EmailSender {

    void send(String to, String subject, String htmlBody);
}
