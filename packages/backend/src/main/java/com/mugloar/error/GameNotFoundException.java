package com.mugloar.error;

/** A referenced game is not tracked by this backend (never started or evicted). */
public class GameNotFoundException extends ApiException {

  public GameNotFoundException(String gameId) {
    super(404, "GAME_NOT_FOUND", "Game \"" + gameId + "\" was not found. Start a new game first.");
  }
}
