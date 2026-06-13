package com.rota.endpoints.web;

import com.rota.documents.api.DocumentVersionNotEditableException;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.endpoints.internal.DuplicateOperationException;
import com.rota.endpoints.internal.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps endpoints-module errors (incl. the documents-module guard's) to ProblemDetail. */
@RestControllerAdvice(assignableTypes = {CategoryController.class, EndpointController.class})
public class EndpointsExceptionHandler {

    @ExceptionHandler({NotFoundException.class, DocumentVersionNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "not_found");
        return problem;
    }

    @ExceptionHandler(DocumentVersionNotEditableException.class)
    public ProblemDetail handleNotEditable(DocumentVersionNotEditableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Version is not editable");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "version_not_editable");
        return problem;
    }

    @ExceptionHandler(DuplicateOperationException.class)
    public ProblemDetail handleDuplicateOperation(DuplicateOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Duplicate operation");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "duplicate_operation");
        return problem;
    }

    /** Race-window fallback for DB unique constraints behind the friendly pre-checks. */
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
