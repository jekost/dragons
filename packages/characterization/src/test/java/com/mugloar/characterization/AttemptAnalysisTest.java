package com.mugloar.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mugloar.characterization.AttemptAnalysis.Rate;
import com.mugloar.characterization.Experiments.Attempt;
import com.mugloar.characterization.Experiments.BoardRow;
import com.mugloar.characterization.Experiments.RewardArm;
import com.mugloar.characterization.Experiments.UpgradeArm;
import com.mugloar.domain.ProbabilityTier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic that interprets an experiment. A live run costs the better part of an hour, so if
 * this maths is quietly wrong the cost of finding out is a whole re-run — which is exactly why it
 * was kept pure and free of API contact.
 */
class AttemptAnalysisTest {

  private static Attempt attempt(int turn, int level, ProbabilityTier tier, boolean success) {
    return new Attempt(0, UpgradeArm.NONE, RewardArm.NA, Experiments.ExpiryArm.NA, turn, level, 3, 0, 0, tier,
        "ad" + turn, "Do a thing", 50, 5, success);
  }

  private static Attempt withReward(int reward) {
    return new Attempt(0, UpgradeArm.NONE, RewardArm.NA, Experiments.ExpiryArm.NA, 0, 0, 3, 0, 0,
        ProbabilityTier.SURE_THING, "ad", "Do a thing", reward, 5, true);
  }

  // --- median reward --------------------------------------------------------

  @Test
  void medianRewardIsTheMiddleValueWithItsSampleSize() {
    assertEquals("50 (n=3)", AttemptAnalysis.medianReward(
        List.of(withReward(10), withReward(90), withReward(50))));
  }

  /** Nothing sampled is a dash, not a median of zero — an empty arm has to stay visible. */
  @Test
  void medianRewardOfNothingIsADash() {
    assertEquals("—", AttemptAnalysis.medianReward(List.of()));
  }


  // --- buckets -------------------------------------------------------------

  @Test
  void turnBucketsSplitAtTenAndTwenty() {
    assertEquals("00-09", AttemptAnalysis.turnBucket(0));
    assertEquals("00-09", AttemptAnalysis.turnBucket(9));
    assertEquals("10-19", AttemptAnalysis.turnBucket(10));
    assertEquals("10-19", AttemptAnalysis.turnBucket(19));
    assertEquals("20+", AttemptAnalysis.turnBucket(20));
  }

  @Test
  void levelBucketsSplitAtZeroTwoAndFour() {
    assertEquals("0", AttemptAnalysis.levelBucket(0));
    assertEquals("1-2", AttemptAnalysis.levelBucket(1));
    assertEquals("1-2", AttemptAnalysis.levelBucket(2));
    assertEquals("3-4", AttemptAnalysis.levelBucket(3));
    assertEquals("3-4", AttemptAnalysis.levelBucket(4));
    assertEquals("5+", AttemptAnalysis.levelBucket(5));
  }

  /**
   * The published vocabulary must be exactly what the bucketers can return. Reports iterate the
   * constants and match cells by string equality, so a boundary changed in one place and not the
   * other yields a silently all-dashes table rather than a failure.
   */
  @Test
  void publishedBucketsMatchWhatTheBucketersReturn() {
    List<String> turns = List.of(0, 5, 9, 10, 15, 19, 20, 50, 500).stream()
        .map(AttemptAnalysis::turnBucket).distinct().toList();
    assertEquals(AttemptAnalysis.TURN_BUCKETS, turns);

    List<String> levels = List.of(0, 1, 2, 3, 4, 5, 6, 20).stream()
        .map(AttemptAnalysis::levelBucket).distinct().toList();
    assertEquals(AttemptAnalysis.LEVEL_BUCKETS, levels);
  }

  // --- Rate ----------------------------------------------------------------

  @Test
  void rateCountsSuccessesOutOfAttempts() {
    Rate rate = new Rate(0, 0).plus(true).plus(false).plus(true).plus(true);
    assertEquals(3, rate.success());
    assertEquals(4, rate.total());
    assertEquals(75, rate.percent());
    assertEquals("75% (3/4)", rate.format());
  }

  /** Nothing sampled renders as a dash — never as a fake 0%, which would read as "always fails". */
  @Test
  void anUnsampledRateRendersAsADash() {
    Rate empty = new Rate(0, 0);
    assertEquals(0.0, empty.fraction());
    assertEquals("—", empty.format());
  }

  @Test
  void rateOfAppliesTheFilter() {
    List<Attempt> attempts = List.of(
        attempt(1, 0, ProbabilityTier.GAMBLE, true),
        attempt(2, 0, ProbabilityTier.GAMBLE, false),
        attempt(3, 0, ProbabilityTier.RISKY, true));

    assertEquals("50% (1/2)",
        AttemptAnalysis.rateOf(attempts, a -> a.tier() == ProbabilityTier.GAMBLE).format());
    assertEquals("67% (2/3)", AttemptAnalysis.rateOf(attempts, a -> true).format());
  }

  // --- crosstab ------------------------------------------------------------

  /**
   * Every row must carry the whole column set. A ragged table would let a missing combination read
   * as though it had been sampled, instead of showing the gap.
   */
  @Test
  void crosstabIsRectangularEvenWhenACombinationIsMissing() {
    List<Attempt> attempts = List.of(
        attempt(1, 0, ProbabilityTier.GAMBLE, true),
        attempt(25, 0, ProbabilityTier.GAMBLE, false),
        attempt(1, 0, ProbabilityTier.RISKY, true));

    Map<String, Map<String, Rate>> table = AttemptAnalysis.crosstab(
        attempts, a -> a.tier().display(), a -> AttemptAnalysis.turnBucket(a.turn()));

    assertEquals(List.of("00-09", "20+"), AttemptAnalysis.columnsOf(table));
    table.values().forEach(row -> assertEquals(List.of("00-09", "20+"), List.copyOf(row.keySet())));

    assertEquals("100% (1/1)", table.get("Gamble").get("00-09").format());
    assertEquals("0% (0/1)", table.get("Gamble").get("20+").format());
    // Risky was never attempted after turn 20 — that cell is a visible gap, not a zero.
    assertEquals("—", table.get("Risky").get("20+").format());
  }

  @Test
  void columnsOfAnEmptyTableIsEmpty() {
    assertTrue(AttemptAnalysis.columnsOf(Map.of()).isEmpty());
  }

  // --- median reward by bucket --------------------------------------------

  private static BoardRow offered(int turn, int level, int reward) {
    return new BoardRow(0, turn, level, UpgradeArm.NONE, "ad" + turn + "-" + reward,
        ProbabilityTier.GAMBLE, reward, 5, null, false);
  }

  /**
   * This is the "rewards climb with the turn counter" finding, which is the whole reason the reward
   * experiment exists — so the arithmetic behind it is worth pinning.
   */
  @Test
  void medianRewardIsReportedPerTurnWindowAndLevel() {
    List<BoardRow> board = List.of(
        offered(1, 0, 10), offered(2, 0, 20), offered(3, 0, 30),
        offered(25, 0, 100), offered(26, 0, 300));

    Map<String, Map<String, String>> table = AttemptAnalysis.medianRewardByBucket(board);

    assertEquals("20 (n=3)", table.get("00-09").get("0"));
    assertEquals("300 (n=2)", table.get("20+").get("0"));
  }

  /** Every window/level combination gets a cell, so a gap reads as a gap and not as a zero. */
  @Test
  void unsampledCombinationsRenderAsADash() {
    Map<String, Map<String, String>> table =
        AttemptAnalysis.medianRewardByBucket(List.of(offered(1, 0, 10)));

    assertEquals(AttemptAnalysis.TURN_BUCKETS, List.copyOf(table.keySet()));
    table.values().forEach(row -> assertEquals(AttemptAnalysis.LEVEL_BUCKETS, List.copyOf(row.keySet())));
    assertEquals("10 (n=1)", table.get("00-09").get("0"));
    assertEquals("—", table.get("00-09").get("5+"));
    assertEquals("—", table.get("20+").get("0"));
  }
}
