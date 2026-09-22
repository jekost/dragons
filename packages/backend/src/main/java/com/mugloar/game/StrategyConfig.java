package com.mugloar.game;

/**
 * Tuning for the strategy engine.
 *
 * @param healThreshold buy a healing potion when lives fall to or below this
 * @param safeProbability the only floor for attempting an ad: below it the board is judged unsafe
 * @param upgradeGoldReserve gold an upgrade purchase must leave behind, so there is always potion
 *     money. The cheapest upgrade is 100 against a 50-gold potion, so an ungated upgrade is two
 *     lives the dragon then cannot buy.
 * @param goldDumpThreshold above this, gold is surplus: spend it on a level rather than sit on it
 */
public record StrategyConfig(
    int healThreshold, double safeProbability, int upgradeGoldReserve, int goldDumpThreshold) {

  public static final StrategyConfig DEFAULT = new StrategyConfig(2, 0.7, 300, 400);
}
