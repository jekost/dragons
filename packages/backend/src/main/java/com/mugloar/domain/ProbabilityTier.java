package com.mugloar.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The risk tiers the API returns, ordered safest → deadliest (ordinal = rank). */
public enum ProbabilityTier {
  SURE_THING("Sure thing"),
  PIECE_OF_CAKE("Piece of cake"),
  WALK_IN_THE_PARK("Walk in the park"),
  QUITE_LIKELY("Quite likely"),
  HMMM("Hmmm...."),
  GAMBLE("Gamble"),
  RISKY("Risky"),
  RATHER_DETRIMENTAL("Rather detrimental"),
  PLAYING_WITH_FIRE("Playing with fire"),
  SUICIDE_MISSION("Suicide mission"),
  IMPOSSIBLE("Impossible");

  private static final Map<String, ProbabilityTier> BY_DISPLAY = Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ProbabilityTier::display, Function.identity()));

  private final String display;

  ProbabilityTier(String display) {
    this.display = display;
  }

  public String display() {
    return display;
  }

  /** 0 = safest. */
  public int rank() {
    return ordinal();
  }

  /**
   * Maps an API label back to its tier; empty for a label this build has never seen, which is how
   * callers detect an unrecognised tier instead of guessing at its odds. The null check is load
   * bearing: {@code BY_DISPLAY} is immutable, and immutable maps throw on a null key lookup.
   */
  public static Optional<ProbabilityTier> fromDisplay(String value) {
    return value == null ? Optional.empty() : Optional.ofNullable(BY_DISPLAY.get(value));
  }
}
