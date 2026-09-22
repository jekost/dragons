package com.mugloar.web;

import com.mugloar.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to the structured {@link ApiError} body. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApi(ApiException e) {
    return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage(), e.status()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e) {
    return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "Route not found.", 404));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleOther(Exception e) {
    String message = e.getMessage() != null ? e.getMessage() : "Unexpected error";
    return ResponseEntity.status(500).body(ApiError.of("INTERNAL_ERROR", message, 500));
  }
}
