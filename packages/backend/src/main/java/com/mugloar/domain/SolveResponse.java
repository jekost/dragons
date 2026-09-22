package com.mugloar.domain;

/** Response from POST /solve/{adId}. Note: no {@code level} field. */
public record SolveResponse(
    boolean success,
    int lives,
    int gold,
    int score,
    int highScore,
    int turn,
    String message) {}
