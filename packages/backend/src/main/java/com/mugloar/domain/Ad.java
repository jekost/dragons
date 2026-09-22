package com.mugloar.domain;

/**
 * A decoded quest. {@code encrypted} is null for plaintext, 1 for Base64, 2 for ROT13 — the
 * string fields here are already decoded. {@code probability} is the raw tier text from the API.
 */
public record Ad(
    String adId,
    String message,
    int reward,
    int expiresIn,
    Integer encrypted,
    String probability) {

  /**
   * Stand-in for an ad the caller named but the board no longer offers — it expired, or the board
   * re-rolled between the user seeing it and clicking it. Valued as the worst possible tier so no
   * strategy path ever prefers it.
   */
  public static Ad unknown(String adId) {
    return new Ad(adId, "(unknown ad)", 0, 0, null, ProbabilityTier.IMPOSSIBLE.display());
  }
}
