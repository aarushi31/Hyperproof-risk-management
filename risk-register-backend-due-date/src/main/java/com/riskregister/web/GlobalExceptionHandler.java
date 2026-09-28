package com.riskregister.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.riskregister.exception.BadRequestException;
import com.riskregister.exception.BusinessRuleException;
import com.riskregister.exception.NotFoundException;
import com.riskregister.web.dto.ApiError;
import com.riskregister.web.dto.ApiError.FieldIssue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Translates exceptions into a single, predictable {@link ApiError} shape. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Object> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<Object> handleBusinessRule(BusinessRuleException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error.", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldIssue(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String message = "Validation failed: " + issues.stream()
                .map(FieldIssue::message).collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, issues);
    }

    /** Malformed JSON, wrong types (e.g. 3.5 or "abc" for an integer), or unknown enum values. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = "Malformed or unreadable request body.";
        List<FieldIssue> issues = List.of();

        if (ex.getCause() instanceof MismatchedInputException mie) {
            String field = mie.getPath().isEmpty() ? null : mie.getPath().get(mie.getPath().size() - 1).getFieldName();
            String name = field != null ? field : "value";
            Class<?> target = mie.getTargetType();

            if (target != null && target.isEnum()) {
                Object supplied = mie instanceof InvalidFormatException ife ? ife.getValue() : null;
                message = "%s has invalid value '%s'. Allowed values: %s"
                        .formatted(name, supplied, Arrays.toString(target.getEnumConstants()));
            } else if (target == Integer.class || target == int.class || target == Long.class) {
                message = name + " must be an integer";
            } else if (target == LocalDate.class) {
                message = name + " must be a date in YYYY-MM-DD format";
            } else {
                message = name + " has an invalid value or type";
            }
            if (field != null) {
                issues = List.of(new FieldIssue(field, message));
            }
        }
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, issues);
    }

    private ResponseEntity<Object> build(HttpStatus status, String code, String message, List<FieldIssue> issues) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, issues);
        return ResponseEntity.status(status).body(body);
    }
}
