package com.mugloar.domain;

/** Response from POST /shop/buy/{itemId}. Includes {@code level}. */
public record BuyResponse(
    boolean shoppingSuccess,
    int gold,
    int lives,
    int level,
    int turn) {}
