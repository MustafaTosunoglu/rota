package com.rota.consumers.web;

import com.rota.consumers.internal.ConsumerNotFoundException;
import com.rota.consumers.internal.GroupNameAlreadyInUseException;
import com.rota.consumers.internal.InvalidInvitationTokenException;
import com.rota.consumers.internal.MemberAlreadyAcceptedException;
import com.rota.documents.api.DocumentNotFoundException;
import com.rota.endpoints.api.EndpointNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps consumers-module errors to RFC 7807 ProblemDetail responses. */
@RestControllerAdvice(assignableTypes = {ConsumerGroupController.class, InvitationController.class})
public class ConsumersExceptionHandler {

    @ExceptionHandler({ConsumerNotFoundException.class, DocumentNotFoundException.class,
            EndpointNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "not_found");
        return problem;
    }

    @ExceptionHandler({GroupNameAlreadyInUseException.class, MemberAlreadyAcceptedException.class})
    public ProblemDetail handleConflicts(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "conflict");
        return problem;
    }

    @ExceptionHandler(InvalidInvitationTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidInvitationTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid or expired invitation");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "invalid_invitation_token");
        return problem;
    }

    /** Race-window fallback for the unique constraints behind the friendly pre-checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail("The change conflicts with existing data (duplicate value or a row still in use).");
        problem.setProperty("code", "conflict");
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
