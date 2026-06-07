package com.rota.iam.web;

import com.rota.iam.internal.AuthService;
import com.rota.iam.internal.AuthService.AuthTokens;
import com.rota.iam.internal.RegistrationService;
import com.rota.iam.internal.RegistrationService.RegistrationCommand;
import com.rota.iam.internal.RegistrationService.RegistrationResult;
import com.rota.iam.jpa.UserEntity;
import com.rota.iam.jpa.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER = "Bearer";

    private final RegistrationService registrationService;
    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(RegistrationService registrationService,
                          AuthService authService,
                          UserRepository userRepository) {
        this.registrationService = registrationService;
        this.authService = authService;
        this.userRepository = userRepository;
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

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toTokenResponse(authService.login(normalizeEmail(request.email()), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toTokenResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity user = userRepository.findById(userId).orElseThrow();
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new MeResponse(userId, jwt.getClaimAsString("tenant_id"),
                user.getEmail(), user.getDisplayName(), roles == null ? List.of() : roles);
    }

    private static TokenResponse toTokenResponse(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), BEARER, tokens.expiresInSeconds());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLocale(String locale) {
        return (locale == null || locale.isBlank()) ? "tr" : locale.trim();
    }
}
