package com.mugloar.domain;

/**
 * Standing with the three factions, from {@code investigate/reputation}.
 *
 * <p>Doubles, not ints. Standing climbs by roughly a tenth per solved quest, so integer fields read
 * 0 for the first half-dozen solves of every game and trailed a whole point behind ever after —
 * Jackson truncates toward zero, so an upstream 1.9 arrived as 1. The API also sends accumulated
 * binary noise (0.1 + 0.2 comes back as 0.30000000000000004), hence the two-decimal rounding.
 */
public record Reputation(double people, double state, double underworld) {

  public Reputation {
    people = round(people);
    state = round(state);
    underworld = round(underworld);
  }

  private static double round(double value) {
    return Math.round(value * 100) / 100.0;
  }
}
