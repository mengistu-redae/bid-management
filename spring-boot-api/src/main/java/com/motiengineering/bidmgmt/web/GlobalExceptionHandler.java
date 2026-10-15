package com.motiengineering.bidmgmt.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Without this, a guarded-transition rejection (BidStatusService et al.
 * throw IllegalStateException/IllegalArgumentException) or a not-found
 * lookup (NoSuchElementException) fell through to Spring Boot's default
 * error handling, which returns a bare 500 with no message by default -
 * the real "Cannot move a bid from X to Y" text never reached the UI.
 * This maps each to a sensible status with the exception's own message
 * in the body, so node-bff's apiClient can surface it directly.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String message) {
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorBody> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(ex.getMessage()));
    }
}
