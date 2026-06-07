package com.rota.iam.web;

import com.rota.iam.internal.EmailAlreadyInUseException;
import com.rota.iam.internal.EmailNotVerifiedException;
import com.rota.iam.internal.InvalidCredentialsException;
import com.rota.iam.internal.InvalidRefreshTokenException;
import com.rota.iam.internal.InvalidVerificationTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps auth/registration errors to clean HTTP responses (RFC 7807 ProblemDetail). */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ProblemDetail handleEmailInUse(EmailAlreadyInUseException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Email already in use");
        problem.setDetail("An account with this email address already exists.");
        problem.setProperty("code", "email_already_in_use");
        return problem;
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ProblemDetail handleUnauthorized(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication failed");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "unauthorized");
        return problem;
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Email not verified");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "email_not_verified");
        return problem;
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ProblemDetail handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid or expired link");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "invalid_verification_token");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail(details.isBlank() ? "Invalid request" : details);
        problem.setProperty("code", "validation_failed");
        return problem;
    }
}
