package com.mugloar.characterization;

import com.mugloar.domain.ProbabilityTier;
import com.mugloar.game.Probabilities;
import tools.jackson.databind.ObjectMapper;

/**
 * The valuation as it stood before reward ceilings: a tier is worth its label whatever the ad
 * costs. Used only as the control arm of the solver benchmark.
 *
 * <p>Subclassing rather than shipping a second resource file or a production flag keeps the
 * control honest — it reads the very same {@code probabilities.json} the live arm does, so the two
 * arms cannot drift apart in anything except the behaviour under test.
 */
final class LabelOnlyProbabilities extends Probabilities {

  LabelOnlyProbabilities(ObjectMapper mapper) {
    super(mapper, "/probabilities.json");
  }

  @Override
  public double successProbability(ProbabilityTier tier, int reward) {
    return successProbability(tier);
  }
}
