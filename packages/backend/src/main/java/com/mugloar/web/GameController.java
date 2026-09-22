package com.mugloar.web;

import com.mugloar.domain.GameSnapshot;
import com.mugloar.domain.InvestigationResult;
import com.mugloar.domain.StepResult;
import com.mugloar.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

  private final GameService service;

  @PostMapping("/games")
  @ResponseStatus(HttpStatus.CREATED)
  public GameSnapshot start() {
    return service.startGame();
  }

  @GetMapping("/games/{id}")
  public GameSnapshot snapshot(@PathVariable String id) {
    return service.getSnapshot(Validators.requireId("gameId", id));
  }

  @PostMapping("/games/{id}/auto-step")
  public StepResult autoStep(@PathVariable String id) {
    return service.autoStep(Validators.requireId("gameId", id));
  }

  /** Costs a turn upstream, so it is a POST like every other move — not a GET. */
  @PostMapping("/games/{id}/investigate")
  public InvestigationResult investigate(@PathVariable String id) {
    return service.investigate(Validators.requireId("gameId", id));
  }

  @PostMapping("/games/{id}/solve/{adId}")
  public StepResult solve(@PathVariable String id, @PathVariable String adId) {
    return service.manualSolve(Validators.requireId("gameId", id), Validators.requireId("adId", adId));
  }

  @PostMapping("/games/{id}/buy/{itemId}")
  public StepResult buy(@PathVariable String id, @PathVariable String itemId) {
    return service.manualBuy(Validators.requireId("gameId", id), Validators.requireId("itemId", itemId));
  }
}
