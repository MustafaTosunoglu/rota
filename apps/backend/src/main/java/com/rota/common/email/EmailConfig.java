package com.rota.common.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the active {@link EmailSender} from {@code rota.email.provider}:
 * {@code smtp} (default, requires {@code spring.mail.host} — dev maildev) or {@code resend}
 * (prod HTTP API). With neither satisfied (e.g. tests) a no-op logging sender is used.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);
    private static final String RESEND_API_BASE_URL = "https://api.resend.com";

    @Bean
    @Conditional(SmtpSelected.class)
    EmailSender smtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        return new SmtpEmailSender(mailSender, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rota.email", name = "provider", havingValue = "resend")
    EmailSender resendEmailSender(ObjectMapper objectMapper, EmailProperties properties) {
        if (properties.getResendApiKey() == null || properties.getResendApiKey().isBlank()) {
            // Fail fast at startup: a silently keyless Resend would drop every email at runtime.
            throw new IllegalStateException(
                    "rota.email.provider=resend but rota.email.resend-api-key is not set");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new ResendEmailSender(httpClient, objectMapper, properties, RESEND_API_BASE_URL);
    }

    /** Fallback when no provider is configured (e.g. tests): logs and drops. */
    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        return (to, subject, htmlBody) ->
                log.warn("No email provider configured; dropping email to {} (subject: {})",
                        to, subject);
    }

    /** SMTP is active when the provider is smtp (or unset) AND an SMTP host is configured. */
    static class SmtpSelected extends AllNestedConditions {

        SmtpSelected() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "rota.email", name = "provider",
                havingValue = "smtp", matchIfMissing = true)
        static class ProviderIsSmtp {
        }

        @ConditionalOnProperty(prefix = "spring.mail", name = "host")
        static class SmtpHostConfigured {
        }
    }
}
