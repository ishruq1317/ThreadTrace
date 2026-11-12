package com.threadtrace.shared;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainExceptions.ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(DomainExceptions.ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://threadtrace.local/problems/not-found"));
        return pd;
    }

    @ExceptionHandler(DomainExceptions.DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DomainExceptions.DuplicateResourceException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Resource");
        pd.setType(URI.create("https://threadtrace.local/problems/duplicate-resource"));
        return pd;
    }

    @ExceptionHandler({
            DomainExceptions.MassBalanceViolationException.class,
            DomainExceptions.InvalidLotStateException.class,
            DomainExceptions.DomainValidationException.class
    })
    public ProblemDetail handleUnprocessable(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        pd.setTitle("Domain Rule Violation");
        pd.setType(URI.create("https://threadtrace.local/problems/domain-rule-violation"));
        return pd;
    }

    @ExceptionHandler({DomainExceptions.BrandAccessDeniedException.class, AccessDeniedException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Access Denied");
        pd.setType(URI.create("https://threadtrace.local/problems/access-denied"));
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Request Validation Failed");
        pd.setType(URI.create("https://threadtrace.local/problems/validation-error"));
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }
}
