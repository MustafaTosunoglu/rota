package com.rota.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Wires the active {@link EmailSender}: SMTP when {@code spring.mail.host} is set, else a no-op log. */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    EmailSender smtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        return new SmtpEmailSender(mailSender, properties);
    }

    /** Fallback when no SMTP is configured (e.g. tests, or a misconfigured env): logs and drops. */
    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        return (to, subject, htmlBody) ->
                log.warn("No SMTP configured (spring.mail.host unset); dropping email to {} (subject: {})",
                        to, subject);
    }
}
