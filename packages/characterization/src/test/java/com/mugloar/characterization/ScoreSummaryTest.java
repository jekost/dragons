package com.mugloar.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mugloar.characterization.Experiments.BenchGame;
import com.mugloar.characterization.Experiments.ValuationArm;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The benchmark's arithmetic. A score run costs the better part of an hour, and a median computed
 * wrongly would quietly decide whether a change ships.
 */
class ScoreSummaryTest {

  private static BenchGame game(int score, boolean died) {
    return new BenchGame(ValuationArm.CEILINGS, score, 40, died, "ok");
  }

  @Test
  void medianIsTheMiddleScoreNotTheMean() {
    var played = List.of(game(100, true), game(200, false), game(9000, false));
    var s = AttemptAnalysis.summarise(played, 1000);
    assertEquals(200, s.median(), "one runaway game must not drag the median");
    assertEquals(3100, s.mean());
    assertEquals(100, s.min());
    assertEquals(9000, s.max());
  }

  @Test
  void countsGoalReachedAndDeaths() {
    var played = List.of(game(999, true), game(1000, false), game(2500, true));
    var s = AttemptAnalysis.summarise(played, 1000);
    assertEquals(2, s.reachedGoal(), "1000 exactly counts as reaching the goal");
    assertEquals(2, s.died());
    assertEquals(3, s.games());
  }

  @Test
  void goalRateIsAFractionOfGamesPlayed() {
    var s = AttemptAnalysis.summarise(List.of(game(1200, false), game(10, true)), 1000);
    assertEquals(0.5, s.goalRate());
  }

  /** An arm that never ran must not divide by zero on the way into the report. */
  @Test
  void survivesAnEmptyArm() {
    var s = AttemptAnalysis.summarise(List.of(), 1000);
    assertEquals(0, s.games());
    assertEquals(0.0, s.goalRate());
  }
}
