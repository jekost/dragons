package com.mugloar.characterization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mugloar.characterization.Experiments.Attempt;
import com.mugloar.characterization.Experiments.ExpiryArm;
import com.mugloar.characterization.Experiments.ExpiryResult;
import com.mugloar.characterization.Experiments.RewardArm;
import com.mugloar.characterization.Experiments.UpgradeArm;
import com.mugloar.domain.ProbabilityTier;
import com.mugloar.game.Probabilities;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * Rendering the expiry report. This runs after twenty minutes of live API calls, so a crash or an
 * empty table there costs a whole re-run — the same reason the arithmetic is kept pure and checked
 * offline.
 */
class ExpiryReportTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private static ReportWriter writer() {
    return new ReportWriter(new Probabilities(MAPPER), MAPPER);
  }

  private static Attempt attempt(ProbabilityTier tier, ExpiryArm arm, int reward, boolean success) {
    int expiresIn = arm == ExpiryArm.EXPIRING ? Experiments.EXPIRING_AT : Experiments.FRESH_AT + 2;
    return new Attempt(0, UpgradeArm.NONE, RewardArm.NA, arm, 12, 0, 3, 0, 0, tier,
        "ad", "Do a thing", reward, expiresIn, success);
  }

  /** A pair of arms on one tier, with the expiring side failing more often. */
  private static ExpiryResult result() {
    List<Attempt> attempts = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      attempts.add(attempt(ProbabilityTier.SURE_THING, ExpiryArm.EXPIRING, 50, i < 6));
      attempts.add(attempt(ProbabilityTier.SURE_THING, ExpiryArm.FRESH, 50, i < 9));
    }
    attempts.add(attempt(ProbabilityTier.GAMBLE, ExpiryArm.NA, 50, true));
    return new ExpiryResult(attempts, List.of(), 4, 20260918L, List.of(120, 300));
  }

  @Test
  void rendersBothArmsAndTheirGap() {
    String md = writer().buildExpiryReport(result());
    assertTrue(md.contains("EXPIRING"), md);
    assertTrue(md.contains("FRESH"), md);
    assertTrue(md.contains("Sure thing"), md);
    // 60% expiring against 90% fresh.
    assertTrue(md.contains("-30.0pp"), md);
  }

  /** The seed is what makes a run reproducible, so it has to survive into the report. */
  @Test
  void recordsTheSeedAndTheGameCount() {
    String md = writer().buildExpiryReport(result());
    assertTrue(md.contains("20260918"), md);
    assertTrue(md.contains("4 live games"), md);
  }

  /** Unpaired turns are logged but must not reach the contrast. */
  @Test
  void countsOnlyPairedAttemptsAsEvidence() {
    String md = writer().buildExpiryReport(result());
    assertTrue(md.contains("21 attempts (20 paired)"), md);
    // The NA turn's tier never paired, so it earns no row in the per-tier table.
    assertFalse(md.contains("| Gamble |"), md);
  }

  /** An empty run must still render rather than throw on the way out of a failed experiment. */
  @Test
  void survivesARunThatNeverPaired() {
    var empty = new ExpiryResult(List.of(), List.of(), 0, 1L, List.of());
    String md = writer().buildExpiryReport(empty);
    assertTrue(md.contains("Expiry Experiment"), md);
  }
}
