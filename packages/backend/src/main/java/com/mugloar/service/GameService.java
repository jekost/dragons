package com.mugloar.service;

import com.mugloar.client.GameApiClient;
import com.mugloar.domain.Ad;
import com.mugloar.domain.BuyResponse;
import com.mugloar.domain.Decision;
import com.mugloar.domain.GameSnapshot;
import com.mugloar.domain.GameState;
import com.mugloar.domain.InvestigationResult;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResponse;
import com.mugloar.domain.StepResult;
import com.mugloar.game.StrategyEngine;
import com.mugloar.store.GameStore;
import com.mugloar.store.StoredGame;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates game flow: starts games, builds snapshots, and executes moves (auto or manual),
 * keeping the session store's state in sync. All strategy lives in {@link StrategyEngine}; this
 * service only wires the engine to the network client and the store.
 */
@Service
@RequiredArgsConstructor
public class GameService {

  private final GameApiClient client;
  private final GameStore store;
  private final StrategyEngine strategy;

  /** One lock per game, so a turn is serialized without blocking any other game. */
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  public GameSnapshot startGame() {
    GameState state = client.startGame();
    List<ShopItem> shop = client.getShop(state.gameId());
    store.create(state, shop);
    return buildSnapshot(state, shop);
  }

  public GameSnapshot getSnapshot(String gameId) {
    return buildSnapshot(store.get(gameId));
  }

  /** Let the strategy engine choose and execute the single best next move. */
  public StepResult autoStep(String gameId) {
    return inTurn(gameId, () -> {
      StoredGame game = store.get(gameId);
      if (isDead(game.state())) {
        return StepResult.ofStop(Decision.stop("No lives left — game over."), buildSnapshot(game));
      }
      Decision decision = strategy.decide(game.state(), currentBoard(game), game.shop());
      return execute(game, decision);
    });
  }

  public StepResult manualSolve(String gameId, String adId) {
    return inTurn(gameId, () -> {
      StoredGame game = store.get(gameId);
      Ad ad = currentBoard(game).stream()
          .filter(a -> a.adId().equals(adId))
          .findFirst()
          .orElseGet(() -> Ad.unknown(adId));
      return execute(game, strategy.manualSolve(ad));
    });
  }

  /**
   * Looks up the dragon's standing with the people, the state and the underworld.
   *
   * <p>This is a turn, not a free readout — the upstream call advances the counter exactly as a
   * solve does, so it runs under the same lock and the snapshot it returns carries the board one
   * turn older than the caller last saw it. Nothing in {@link StrategyEngine} reads reputation;
   * whether it affects outcomes at all is unmeasured, so this exists for the user to look, not for
   * the bot to act on.
   */
  public InvestigationResult investigate(String gameId) {
    return inTurn(gameId, () -> {
      StoredGame game = store.get(gameId);
      if (isDead(game.state())) {
        return new InvestigationResult(null, buildSnapshot(game));
      }
      Reputation reputation = client.getReputation(gameId);
      GameState next = game.state().afterInvestigate();
      store.setState(gameId, next);
      return new InvestigationResult(reputation, buildSnapshot(next, game.shop()));
    });
  }

  public StepResult manualBuy(String gameId, String itemId) {
    return inTurn(gameId, () -> {
      StoredGame game = store.get(gameId);
      ShopItem item = game.shop().stream()
          .filter(i -> i.id().equals(itemId))
          .findFirst()
          .orElseGet(() -> new ShopItem(itemId, itemId, 0));
      return execute(game, Decision.buy(item, "Manual purchase requested by user."));
    });
  }

  /**
   * Carries out a decision. The decision already names its own target, so nothing else needs to be
   * passed alongside it — that is what keeps an executed move and its logged reason in step.
   */
  private StepResult execute(StoredGame game, Decision decision) {
    // No default arm on purpose: the switch is exhaustive over Decision.Type, so a new kind of
    // decision fails to compile here rather than quietly being treated as "stop".
    return switch (decision.type()) {
      case BUY -> executeBuy(game, decision);
      case SOLVE -> executeSolve(game, decision);
      case STOP -> StepResult.ofStop(decision, buildSnapshot(game));
    };
  }

  /**
   * Runs one turn with exclusive access to that game. A turn is read-act-write across an upstream
   * call, so without this two overlapping requests — a double-click, or auto-play ticking while a
   * manual move is in flight — would both act and the second write would silently discard the
   * first, diverging this store from the real game.
   *
   * <p>Locks are per game, so different games never wait on each other. A {@link ReentrantLock}
   * rather than {@code synchronized} because the critical section blocks on I/O.
   */
  private <T> T inTurn(String gameId, Supplier<T> turn) {
    ReentrantLock lock = locks.computeIfAbsent(gameId, id -> new ReentrantLock());
    lock.lock();
    try {
      return turn.get();
    } finally {
      lock.unlock();
    }
  }

  /**
   * The board for the current state, fetched only if the last action did not already cache one.
   * {@link #buildSnapshot} re-reads the board after every action and stores it, so the steady state
   * is a cache hit — which is what keeps a turn to two upstream calls instead of three.
   */
  private List<Ad> currentBoard(StoredGame game) {
    if (!game.ads().isEmpty()) {
      return game.ads();
    }
    List<Ad> ads = client.getMessages(game.state().gameId());
    store.setAds(game.state().gameId(), ads);
    return ads;
  }

  private StepResult executeSolve(StoredGame game, Decision decision) {
    String gameId = game.state().gameId();
    SolveResponse result = client.solve(gameId, decision.adId());
    GameState next = store.get(gameId).state().afterSolve(result);
    store.setState(gameId, next);
    return StepResult.ofSolve(decision, result, buildSnapshot(next, game.shop()));
  }

  private StepResult executeBuy(StoredGame game, Decision decision) {
    String gameId = game.state().gameId();
    BuyResponse result = client.buy(gameId, decision.itemId());
    GameState next = store.get(gameId).state().afterBuy(result);
    store.setState(gameId, next);
    return StepResult.ofBuy(decision, result, buildSnapshot(next, game.shop()));
  }

  /**
   * A dead game's endpoints return 410 Gone, so every path that would otherwise call upstream has
   * to ask this first. That guard is what lets a fatal solve return a clean game-over result
   * instead of throwing.
   */
  private static boolean isDead(GameState state) {
    return state.lives() <= 0;
  }

  private GameSnapshot buildSnapshot(StoredGame game) {
    return buildSnapshot(game.state(), game.shop());
  }

  private GameSnapshot buildSnapshot(GameState state, List<ShopItem> shop) {
    if (isDead(state)) {
      store.setAds(state.gameId(), List.of());
      return new GameSnapshot(
          state, shop, List.of(), strategy.decide(state, List.of(), shop));
    }
    List<Ad> ads = client.getMessages(state.gameId());
    // Cache the board against the state it belongs to: the next turn reuses it instead of
    // re-fetching the identical list. Every action ends here, so the cache is always current.
    store.setAds(state.gameId(), ads);
    Decision recommendation = strategy.decide(state, ads, shop);
    return new GameSnapshot(state, shop, ads, recommendation);
  }
}
