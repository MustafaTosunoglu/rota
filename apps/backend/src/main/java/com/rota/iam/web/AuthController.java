package com.rota.iam.web;

import com.rota.iam.internal.RegistrationService;
import com.rota.iam.internal.RegistrationService.RegistrationCommand;
import com.rota.iam.internal.RegistrationService.RegistrationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResult result = registrationService.register(new RegistrationCommand(
                normalizeEmail(request.email()),
                request.password(),
                request.displayName().trim(),
                request.organizationName().trim(),
                normalizeLocale(request.locale())));
        return new RegisterResponse(result.tenantId(), result.userId());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLocale(String locale) {
        return (locale == null || locale.isBlank()) ? "tr" : locale.trim();
    }
}
