package com.mugloar.error;

/** The upstream game API returned an error or was unreachable. */
public class GameApiException extends ApiException {

  private final Integer upstreamStatus;

  public GameApiException(int status, String message, Integer upstreamStatus) {
    super(status, "GAME_API_ERROR", message);
    this.upstreamStatus = upstreamStatus;
  }

  public GameApiException(int status, String message) {
    this(status, message, null);
  }

  public Integer upstreamStatus() {
    return upstreamStatus;
  }
}
