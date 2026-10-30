package com.shopsphere.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) { return build(e.getStatus(), e.getCode(), e.getMessage(), null); }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) { return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), null); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> f = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> f.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.", f);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e) { return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage(), null); }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
                       HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> badRequest(Exception e) {
        String msg = e instanceof IllegalArgumentException ? e.getMessage() : "Invalid request.";
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) { return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource.", null); }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(Exception e) { return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Images must be 2 MB or smaller.", null); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled exception", e);   // details stay in logs, never in the response
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.", null);
    }

    private ResponseEntity<ApiError> build(HttpStatus s, String code, String msg, Map<String, String> fields) {
        return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), code, msg, fields));
    }
}
