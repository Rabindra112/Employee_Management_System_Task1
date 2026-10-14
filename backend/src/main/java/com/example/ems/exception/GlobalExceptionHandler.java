package com.example.ems.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // COMMON ERROR RESPONSE
    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
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
            HttpMessageNotReadableException.class
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