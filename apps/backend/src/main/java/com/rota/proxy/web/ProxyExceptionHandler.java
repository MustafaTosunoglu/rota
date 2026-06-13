package com.rota.proxy.web;

import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.endpoints.api.EndpointNotFoundException;
import com.rota.proxy.internal.ProxyBadRequestException;
import com.rota.proxy.internal.ProxyExecutionException;
import com.rota.proxy.internal.QuotaExceededException;
import com.rota.proxy.internal.SsrfBlockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps proxy errors to RFC 7807 ProblemDetail responses. */
@RestControllerAdvice(assignableTypes = ProxyController.class)
public class ProxyExceptionHandler {

    @ExceptionHandler({SsrfBlockedException.class, ProxyBadRequestException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Request not allowed");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "proxy_blocked");
        return problem;
    }

    @ExceptionHandler({EndpointNotFoundException.class, DocumentVersionNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "not_found");
        return problem;
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuota(QuotaExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Daily limit reached");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "quota_exceeded");
        return problem;
    }

    @ExceptionHandler(ProxyExecutionException.class)
    public ProblemDetail handleUpstream(ProxyExecutionException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setTitle("Target API unreachable");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "upstream_failed");
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
