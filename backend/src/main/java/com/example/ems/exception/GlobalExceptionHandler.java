package com.example.ems.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // COMMON ERROR RESPONSE
    private ResponseEntity<Map<String, Object>> error(
            HttpStatusCode status,
            String message,
            String path) {

        Map<String, Object> response = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "message", message,
                "path", path
        );

        return ResponseEntity.status(status).body(response);
    }

    // 404 - EMPLOYEE NOT FOUND
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        return error(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    // 400 - VALIDATION ERROR
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);

        return error(
                HttpStatus.BAD_REQUEST,
                message.isEmpty() ? "Invalid input" : message,
                request.getRequestURI()
        );
    }

    // 400 - INVALID REQUEST
    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            Exception ex,
            HttpServletRequest request) {

        log.warn("Invalid request at {}: {}",
                request.getRequestURI(),
                ex.getMessage());

        return error(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters or JSON body",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "You do not have permission to access this resource",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> response = error(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this resource", request.getRequestURI());
        return ResponseEntity.status(response.getStatusCode())
                .allow(ex.getSupportedHttpMethods() == null ? new org.springframework.http.HttpMethod[0]
                        : ex.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new))
                .body(response.getBody());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File size must not exceed 10 MB",
                request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Data conflict at {}", request.getRequestURI());
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data",
                request.getRequestURI());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        return error(
                ex.getStatusCode(),
                ex.getReason() == null ? "Request failed" : ex.getReason(),
                request.getRequestURI()
        );
    }

    // 500 - UNEXPECTED SERVER ERROR
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error",
                request.getRequestURI()
        );
    }
}
