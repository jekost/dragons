package com.mugloar.error;

/** Base for errors that map cleanly to an HTTP response. Always raised through a subclass. */
public abstract class ApiException extends RuntimeException {

  private final int status;
  private final String code;

  protected ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
