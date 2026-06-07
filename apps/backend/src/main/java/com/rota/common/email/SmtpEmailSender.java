package com.rota.common.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/**
 * Sends HTML email over SMTP. In dev this targets maildev (port 1025) — see {@code spring.mail}.
 *
 * <p>Send failures are logged, not rethrown: a transactional email that fails (e.g. maildev
 * down) must never break the user-facing operation that triggered it (signup, password reset).
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Sent email to {} (subject: {})", to, subject);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.warn("Failed to send email to {} (subject: {}): {}", to, subject, ex.getMessage());
        }
    }
}
