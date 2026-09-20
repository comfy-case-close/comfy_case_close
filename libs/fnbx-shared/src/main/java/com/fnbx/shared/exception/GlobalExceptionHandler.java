package com.fnbx.shared.exception;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Imported in each servlet service. Never exposes raw infrastructure exception messages. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> application(AppException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(
                ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraints(ConstraintViolationException ex, HttpServletRequest request) {
        var fields = ex.getConstraintViolations().stream().map(v -> ErrorResponse.FieldError.builder()
                .field(v.getPropertyPath().toString()).message(v.getMessage()).build()).toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage(), request.getRequestURI(), fields));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException ex, HttpServletRequest request) {
        return response(ErrorCode.ACCESS_DENIED, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException ex, HttpServletRequest request) {
        return response(ErrorCode.UNAUTHENTICATED, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        // Log the type only: exception text may include SQL parameters, credentials or tokens.
        logger.error("Unhandled request failure: " + ex.getClass().getName());
        return response(ErrorCode.UNEXPECTED_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null
                && servlet.getResponse().isCommitted()) return null;
        ErrorCode code = ex instanceof HttpMessageNotReadableException ? ErrorCode.MALFORMED_REQUEST
                : ErrorCode.forHttpStatus(status.value());
        List<ErrorResponse.FieldError> fields = ex instanceof BindException bind
                ? bind.getBindingResult().getFieldErrors().stream().map(e -> ErrorResponse.FieldError.builder()
                    .field(e.getField()).message(e.getDefaultMessage()).build()).toList()
                : List.of();
        String path = request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(ErrorResponse.of(code, code.getMessage(), path, fields), responseHeaders, status);
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, request.getRequestURI()));
    }
}
