package com.mugloar.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which kind of decision the strategy reached. An enum rather than a string so that switching over
 * it is exhaustive: a new kind becomes a compile error at every call site instead of silently
 * falling into whichever branch happens to be the default.
 *
 * <p>{@link #wireName} keeps the JSON lowercase. The SPA discriminates its union on
 * {@code 'solve' | 'buy' | 'stop'} (see {@code types/index.ts}), and Jackson would otherwise
 * serialize the constant name and break that contract with nothing to catch it — no backend test
 * inspects the serialized form.
 */
public enum DecisionType {
  SOLVE("solve"),
  BUY("buy"),
  STOP("stop");

  private final String wireName;

  DecisionType(String wireName) {
    this.wireName = wireName;
  }

  @JsonValue
  public String wireName() {
    return wireName;
  }
}
