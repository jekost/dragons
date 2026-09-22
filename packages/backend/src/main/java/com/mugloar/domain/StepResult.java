package com.mugloar.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Result of executing one action. {@code solve}/{@code buy} are null-omitted as appropriate. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepResult(
    Decision decision,
    SolveResponse solve,
    BuyResponse buy,
    GameSnapshot snapshot) {

  public static StepResult ofSolve(Decision decision, SolveResponse solve, GameSnapshot snapshot) {
    return new StepResult(decision, solve, null, snapshot);
  }

  public static StepResult ofBuy(Decision decision, BuyResponse buy, GameSnapshot snapshot) {
    return new StepResult(decision, null, buy, snapshot);
  }

  public static StepResult ofStop(Decision decision, GameSnapshot snapshot) {
    return new StepResult(decision, null, null, snapshot);
  }
}
