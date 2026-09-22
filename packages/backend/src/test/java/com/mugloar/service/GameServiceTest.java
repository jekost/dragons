package com.mugloar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mugloar.client.GameApiClient;
import com.mugloar.domain.DecisionType;
import com.mugloar.domain.Ad;
import com.mugloar.domain.BuyResponse;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResponse;
import com.mugloar.error.GameNotFoundException;
import com.mugloar.game.Probabilities;
import com.mugloar.game.StrategyEngine;
import com.mugloar.store.GameStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GameServiceTest {

  private final GameApiClient client = mock(GameApiClient.class);
  private final GameStore store = new GameStore();
  private final Probabilities probs = new Probabilities(JsonMapper.builder().build(), "/test-probabilities.json");
  private final GameService service = new GameService(client, store, new StrategyEngine(probs));

  private final List<ShopItem> shop = List.of(
      new ShopItem("hpot", "Healing potion", 50),
      new ShopItem("cs", "Claw Sharpening", 100));
  private final Ad safeAd = new Ad("a1", "Easy quest", 60, 5, null, "Sure thing");

  @BeforeEach
  void stubs() {
    when(client.startGame()).thenReturn(new GameState("g1", 3, 0, 0, 0, 0, 0));
    when(client.getShop("g1")).thenReturn(shop);
    when(client.getMessages("g1")).thenReturn(List.of(safeAd));
    when(client.solve(eq("g1"), any())).thenReturn(new SolveResponse(true, 3, 60, 60, 60, 1, "ok"));
    when(client.buy(eq("g1"), any())).thenReturn(new BuyResponse(true, 10, 4, 0, 2));
  }

  @Test
  void startsStoresAndRecommends() {
    var snap = service.startGame();
    assertEquals("g1", snap.state().gameId());
    assertTrue(store.has("g1"));
    assertEquals(DecisionType.SOLVE, snap.recommendation().type());
  }

  @Test
  void autoStepSolvesAndSyncsScore() {
    service.startGame();
    var step = service.autoStep("g1");
    assertEquals(DecisionType.SOLVE, step.decision().type());
    assertEquals(60, step.solve().score());
    assertEquals(60, store.get("g1").state().score());
  }

  @Test
  void autoStepHealsWhenLivesLow() {
    store.create(new GameState("g1", 1, 50, 0, 0, 0, 0), shop);
    var step = service.autoStep("g1");
    assertEquals(DecisionType.BUY, step.decision().type());
    assertEquals("hpot", step.decision().itemId());
    assertEquals(4, store.get("g1").state().lives());
  }

  @Test
  void supportsManualSolve() {
    service.startGame();
    var step = service.manualSolve("g1", "a1");
    assertEquals(DecisionType.SOLVE, step.decision().type());
    assertEquals(60, store.get("g1").state().score());
  }

  /** The only remaining cover for the manual purchase path. */
  @Test
  void supportsManualBuy() {
    service.startGame();
    var step = service.manualBuy("g1", "hpot");
    assertEquals(DecisionType.BUY, step.decision().type());
    assertEquals("hpot", step.decision().itemId());
    assertEquals(4, store.get("g1").state().lives());
  }

  @Test
  void fatalSolveReturnsCleanGameOverInsteadOfThrowing() {
    service.startGame();
    // Solve drops lives to 0 — the dead game's /messages would 410, so buildSnapshot must not call it.
    when(client.solve(eq("g1"), any())).thenReturn(new SolveResponse(false, 0, 60, 300, 300, 5, "died"));
    var step = service.autoStep("g1");
    assertEquals(DecisionType.SOLVE, step.decision().type());
    assertEquals(0, step.solve().lives());
    assertEquals(DecisionType.STOP, step.snapshot().recommendation().type());
    assertEquals(0, store.get("g1").state().lives());
  }

  @Test
  void autoStepOnDeadGameDoesNotCallUpstream() {
    store.create(new GameState("g1", 0, 60, 0, 300, 300, 5), shop);
    var step = service.autoStep("g1");
    assertEquals(DecisionType.STOP, step.decision().type());
    verify(client, never()).getMessages("g1");
  }

  @Test
  void investigateReturnsReputationAndSpendsATurn() {
    service.startGame();
    when(client.getReputation("g1")).thenReturn(new Reputation(1, -2, 0));
    var result = service.investigate("g1");
    assertEquals(new Reputation(1, -2, 0), result.reputation());
    // The reputation response carries no state, so the turn it costs has to be applied locally.
    assertEquals(1, store.get("g1").state().turn());
    assertEquals(1, result.snapshot().state().turn());
  }

  @Test
  void investigateRefreshesTheBoardItJustAged() {
    Ad older = new Ad("a2", "Quest one turn older", 60, 4, null, "Sure thing");
    service.startGame();
    when(client.getReputation("g1")).thenReturn(new Reputation(0, 0, 0));
    when(client.getMessages("g1")).thenReturn(List.of(older));
    var result = service.investigate("g1");
    assertEquals(List.of(older), result.snapshot().ads());
  }

  @Test
  void investigateOnDeadGameDoesNotCallUpstream() {
    store.create(new GameState("g1", 0, 60, 0, 300, 300, 5), shop);
    var result = service.investigate("g1");
    assertNull(result.reputation());
    assertEquals(5, result.snapshot().state().turn());
    verify(client, never()).getReputation("g1");
    verify(client, never()).getMessages("g1");
  }

  @Test
  void unknownGameThrows() {
    assertThrows(GameNotFoundException.class, () -> service.getSnapshot("nope"));
  }
}
