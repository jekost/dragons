package com.mugloar.web;

/** Structured error body: {@code {error: {code, message, status}}} — matches the frontend contract. */
public record ApiError(Body error) {

  public record Body(String code, String message, int status) {}

  public static ApiError of(String code, String message, int status) {
    return new ApiError(new Body(code, message, status));
  }
}
