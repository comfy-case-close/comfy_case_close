package com.comfy.caseclose.exception;

import com.comfy.caseclose.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Two reviewers (approve/reject/void) or two submits raced each other and the loser's
     * transaction lost an {@code @Version} check at commit time — see CashClose#version.
     * A clean, expected 409 instead of the generic 500 handler below.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Optimistic lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(
                HttpStatus.CONFLICT,
                "This record was just changed by someone else. Please refresh and try again.",
                request);
    }

    /**
     * A DB-level unique/foreign-key constraint rejected the write — most commonly two concurrent
     * submits racing CashCloseServiceImpl#requireNoActiveClose's SELECT-then-INSERT and both
     * passing the pre-check, with only one INSERT actually succeeding
     * (uq_cash_closes_branch_date_shift_active). Deliberately a global handler, not a local
     * try/catch in one service, so any future unique-constraint race in the app gets the same
     * clean 409 instead of leaking a raw 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.info("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(
                HttpStatus.CONFLICT,
                "This conflicts with an existing record — it may already have been created by someone else.",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message, request);
    }

    // Thrown for @Validated + @Min/@Max/etc. on @RequestParam / @PathVariable method arguments.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> lastPathSegment(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message, request);
    }

    // Thrown by AuthenticationManager.authenticate() during login (bad passcode, unknown code, inactive account).
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid employee code or passcode", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private String lastPathSegment(String propertyPath) {
        String[] segments = propertyPath.split("\\.");
        return segments[segments.length - 1];
    }
}
