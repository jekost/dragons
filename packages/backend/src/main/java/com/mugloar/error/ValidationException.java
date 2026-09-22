package com.mugloar.error;

/** Request input failed validation. */
public class ValidationException extends ApiException {

  public ValidationException(String message) {
    super(400, "VALIDATION_ERROR", message);
  }
}
