package com.mugloar.domain;

/**
 * Full game state. NOTE: the upstream {@code solve} response carries no {@code level} — only
 * {@code buy} does — so {@link #afterSolve} keeps the existing level while {@link #afterBuy}
 * updates it.
 */
public record GameState(
    String gameId,
    int lives,
    int gold,
    int level,
    int score,
    int highScore,
    int turn) {

  public GameState afterSolve(SolveResponse r) {
    return new GameState(gameId, r.lives(), r.gold(), level, r.score(), r.highScore(), r.turn());
  }

  public GameState afterBuy(BuyResponse r) {
    return new GameState(gameId, r.lives(), r.gold(), r.level(), score, highScore, r.turn());
  }

  /**
   * Investigating reputation is a move: measured against the live API, three consecutive
   * {@code investigate/reputation} calls between two solves advanced the turn counter from 1 to 5.
   * The response body carries only the three reputation figures — no lives, gold or turn — so
   * unlike {@link #afterSolve} and {@link #afterBuy} there is nothing to sync from, and the one
   * turn it costs has to be applied here or the HUD silently drifts a turn behind the real game.
   */
  public GameState afterInvestigate() {
    return new GameState(gameId, lives, gold, level, score, highScore, turn + 1);
  }
}
