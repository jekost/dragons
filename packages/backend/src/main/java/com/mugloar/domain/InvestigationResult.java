package com.mugloar.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of looking up reputation. Shaped like {@link StepResult}: the payload plus a fresh
 * snapshot, because investigating spends a turn and so leaves a one-turn-older board behind.
 *
 * <p>{@code reputation} is omitted when the game is already over — a dead game's endpoints return
 * 410 Gone, so there was nothing to look up.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvestigationResult(Reputation reputation, GameSnapshot snapshot) {}
