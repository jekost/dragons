package com.mugloar.store;

import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import com.mugloar.error.GameNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory session store. The upstream API exposes no "get state" endpoint — state only arrives
 * in start/solve/buy responses — so the backend is the source of truth for a game's live state. A
 * production system would back this with Redis; a concurrent map is right for this scope.
 *
 * <p>Every mutation is a single atomic map operation. A read-then-put would lose updates when two
 * requests touch one game, which the map's own thread-safety does not prevent.
 */
@Component
public class GameStore {

  private final Map<String, StoredGame> games = new ConcurrentHashMap<>();

  public void create(GameState state, List<ShopItem> shop) {
    games.put(state.gameId(), new StoredGame(state, shop, List.of()));
  }

  public boolean has(String gameId) {
    return games.containsKey(gameId);
  }

  public StoredGame get(String gameId) {
    StoredGame game = games.get(gameId);
    if (game == null) {
      throw new GameNotFoundException(gameId);
    }
    return game;
  }

  /** Replaces the state, keeping the shop and the cached board. */
  public void setState(String gameId, GameState state) {
    if (games.computeIfPresent(gameId, (id, old) -> new StoredGame(state, old.shop(), old.ads()))
        == null) {
      throw new GameNotFoundException(gameId);
    }
  }

  /** Caches the board fetched for the current state, so the next turn need not re-fetch it. */
  public void setAds(String gameId, List<Ad> ads) {
    games.computeIfPresent(
        gameId, (id, old) -> new StoredGame(old.state(), old.shop(), ads));
  }
}
