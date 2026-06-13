package com.rota.importer.web;

import com.rota.documents.api.DocumentVersionNotEditableException;
import com.rota.documents.api.DocumentVersionNotFoundException;
import com.rota.importer.internal.ImportParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps importer/exporter errors to RFC 7807 ProblemDetail responses. */
@RestControllerAdvice(assignableTypes = {ImportController.class, ExportController.class})
public class ImporterExceptionHandler {

    @ExceptionHandler(ImportParseException.class)
    public ProblemDetail handleParse(ImportParseException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Cannot parse import");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", "import_parse_failed");
        return problem;
    }

    @ExceptionHandler(DocumentVersionNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentVersionNotFoundException ex) {
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
