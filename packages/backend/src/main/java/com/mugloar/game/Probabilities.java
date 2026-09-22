package com.mugloar.game;

import com.mugloar.domain.ProbabilityTier;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Success-probability table, loaded from the committed {@code probabilities.json} on the
 * classpath — data produced by the characterization tool, not magic numbers in code, so every
 * value is traceable to a measurement.
 */
@Component
public class Probabilities {

  private static final double UNKNOWN_TIER = 0.1;

  private final Map<ProbabilityTier, Double> byTier = new EnumMap<>(ProbabilityTier.class);
  private final Map<ProbabilityTier, Ceiling> ceilings = new EnumMap<>(ProbabilityTier.class);

  /**
   * The reward at which a tier stops predicting, and what it is really worth above that. Measured
   * offline from the pooled attempt CSVs rather than by the sampler, so { characterize}
   * carries these forward instead of regenerating them.
   *
   * @param atOrAbove the first reward the label can no longer be trusted at
   * @param probability the measured success rate at or above that reward
   */
  public record Ceiling(int atOrAbove, double probability, int samples) {}

  @Autowired
  public Probabilities(ObjectMapper mapper) {
    this(mapper, "/probabilities.json");
  }

  public Probabilities(ObjectMapper mapper, String resourcePath) {
    try (InputStream in = Probabilities.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource " + resourcePath);
      }
      JsonNode root = mapper.readTree(in);
      JsonNode tiers = root.get("byTier");
      for (Map.Entry<String, JsonNode> entry : tiers.properties()) {
        ProbabilityTier.fromDisplay(entry.getKey())
            .ifPresent(tier -> byTier.put(tier, entry.getValue().asDouble()));
      }
      readCeilings(root.get("rewardCeilings"));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load " + resourcePath, e);
    }
  }

  /** A tier with no ceiling entry is trusted at any price. */
  private void readCeilings(JsonNode node) {
    if (node == null) {
      return;
    }
    for (Map.Entry<String, JsonNode> entry : node.properties()) {
      JsonNode c = entry.getValue();
      ProbabilityTier.fromDisplay(entry.getKey()).ifPresent(tier -> ceilings.put(tier,
          new Ceiling(c.get("atOrAbove").asInt(), c.get("probability").asDouble(),
              c.get("samples").asInt())));
    }
  }

  /** The label alone, ignoring price. Prefer the reward-aware overload for anything on the board. */
  public double successProbability(ProbabilityTier tier) {
    return byTier.getOrDefault(tier, UNKNOWN_TIER);
  }

  /**
   * What this ad is really worth. Above a tier's ceiling the label stops predicting: a
   * 150-gold "Sure thing" succeeded 9% of the time over 149 attempts, against the 98% its label
   * claims. Returning the measured rate here rather than adding a rule to the strategy means the
   * existing safety floor rejects the ad on its own.
   */
  public double successProbability(ProbabilityTier tier, int reward) {
    Ceiling ceiling = ceilings.get(tier);
    return ceiling != null && reward >= ceiling.atOrAbove()
        ? ceiling.probability()
        : successProbability(tier);
  }

  /** The ceilings as loaded, so the characterization tool can carry them forward untouched. */
  public Map<ProbabilityTier, Ceiling> rewardCeilings() {
    return Map.copyOf(ceilings);
  }

  public double successProbability(String tierDisplay) {
    return ProbabilityTier.fromDisplay(tierDisplay)
        .map(this::successProbability)
        .orElse(UNKNOWN_TIER);
  }
}
