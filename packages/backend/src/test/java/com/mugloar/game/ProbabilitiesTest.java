package com.mugloar.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mugloar.domain.ProbabilityTier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the <em>shipped</em> {@code probabilities.json}, which the rest of the suite never touches
 * because {@link StrategyEngineTest} loads a fixture instead. The reward ceilings in that file are
 * measured offline and only carried forward by {@code characterize}, so nothing else would notice
 * if a regeneration quietly dropped them.
 */
class ProbabilitiesTest {

  private final Probabilities shipped = new Probabilities(JsonMapper.builder().build());

  @Test
  void shippedFileStillCarriesTheRewardCeilings() {
    assertFalse(shipped.rewardCeilings().isEmpty(),
        "probabilities.json lost its rewardCeilings — a regeneration probably dropped them");
    assertEquals(130, shipped.rewardCeilings().get(ProbabilityTier.SURE_THING).atOrAbove());
    assertEquals(150, shipped.rewardCeilings().get(ProbabilityTier.PIECE_OF_CAKE).atOrAbove());
  }

  /** The whole point: a dear "Sure thing" must not read as a near-certainty. */
  @Test
  void pricesASureThingByItsRewardNotOnlyItsLabel() {
    double cheap = shipped.successProbability(ProbabilityTier.SURE_THING, 120);
    double dear = shipped.successProbability(ProbabilityTier.SURE_THING, 130);
    assertTrue(cheap > 0.9, "under the ceiling the label stands, was " + cheap);
    assertTrue(dear < 0.2, "at the ceiling it collapses, was " + dear);
  }

  /** The ceiling is inclusive at its boundary and absent tiers are untouched at any price. */
  @Test
  void leavesTiersWithoutACeilingAtTheirLabel() {
    assertEquals(
        shipped.successProbability(ProbabilityTier.WALK_IN_THE_PARK),
        shipped.successProbability(ProbabilityTier.WALK_IN_THE_PARK, 9000));
  }

  @Test
  void unknownTierLabelsFallBackRatherThanThrow() {
    assertEquals(0.1, shipped.successProbability("Totally made up"));
  }
}
