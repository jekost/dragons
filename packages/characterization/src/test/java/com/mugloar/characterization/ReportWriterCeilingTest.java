package com.mugloar.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mugloar.game.Probabilities;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code characterize} rebuilds probabilities.json from an empty map every run. The reward ceilings
 * are measured offline, so if that rebuild forgets them the solver silently goes back to trusting a
 * 500-gold "Sure thing" at 0.98 — with no test failing anywhere else. This is that test.
 */
class ReportWriterCeilingTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  @Test
  @SuppressWarnings("unchecked")
  void regenerationHandsTheCeilingsBackUnchanged() {
    var writer = new ReportWriter(new Probabilities(MAPPER), MAPPER);
    Map<String, Object> json = new LinkedHashMap<>();
    writer.carryForwardCeilings(json);

    Map<String, Object> ceilings = (Map<String, Object>) json.get("rewardCeilings");
    assertNotNull(ceilings, "a regenerated probabilities.json dropped rewardCeilings");
    Map<String, Object> sureThing = (Map<String, Object>) ceilings.get("Sure thing");
    assertEquals(130, sureThing.get("atOrAbove"));
    assertTrue(json.containsKey("rewardCeilingsComment"),
        "the carried-forward block must say it was not measured by this run");
  }
}
