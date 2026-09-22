package com.mugloar.game;

import com.mugloar.domain.Ad;
import com.mugloar.domain.Decision;
import com.mugloar.domain.GameState;
import com.mugloar.domain.ProbabilityTier;
import com.mugloar.domain.ShopItem;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The pure decision function. Given the current game state, the (already decoded) ad board, and
 * the shop, it returns the single best next action. It has no side effects, which makes it
 * exhaustively unit-testable.
 *
 * <p>Strategy, grounded in live characterization (baseline clears 1000+ ~90% of the time):
 * <ol>
 *   <li>Survival first — top up lives with a healing potion when low.</li>
 *   <li>Otherwise attempt only <em>safe</em> ads — those at or above safeProbability. Risk is
 *       never traded for reward: a tempting long shot is simply not on the table. An ad priced at
 *       or above its tier's reward ceiling is valued at the rate measured there, not at its
 *       label, so an expensive "Sure thing" fails this floor like the coin flip it really is.</li>
 *   <li>Among the safe ads, take the <em>lowest</em> reward. Cheap safe quests are the ones the
 *       board keeps offering, so working through them compounds score without spending lives.</li>
 *   <li>When nothing clears the floor, take the safest ad on the board rather than buy a
 *       re-roll. Gold is for staying alive; a bad board costs a risk, not the potion fund.</li>
 * </ol>
 *
 * <p><b>Upgrades come out of surplus, never out of the potion fund.</b> Buying one whenever the
 * board looked bad drained the gold a potion needs — the cheapest upgrade is 100 against a
 * 50-gold potion. So there is one trigger only: gold above {@code goldDumpThreshold}, and the
 * purchase must still leave {@code upgradeGoldReserve} behind. Below that the dragon waits.
 *
 * <p>1000 is a milestone, not a stop condition: the only natural end is running out of lives, so
 * the bot plays on past 1000 to maximize the score.
 */
@Component
@RequiredArgsConstructor
public class StrategyEngine {

  /** The score the assignment requires — a milestone, not a stop condition. */
  public static final int GOAL_SCORE = 1000;

  public static final String HEALING_POTION_ID = "hpot";

  private final Probabilities probabilities;

  /** Decides with the shipped tuning. */
  public Decision decide(GameState state, List<Ad> ads, List<ShopItem> shop) {
    return decide(state, ads, shop, StrategyConfig.DEFAULT);
  }

  public Decision decide(
      GameState state, List<Ad> ads, List<ShopItem> shop, StrategyConfig config) {
    // The only natural end is death; 1000 is a milestone we play past, not a stop.
    if (state.lives() <= 0) {
      return Decision.stop("No lives left — game over.");
    }

    ShopItem hpot = findItem(shop, HEALING_POTION_ID);

    if (canHeal(state, hpot, config)) {
      return Decision.buy(
          hpot, "Low on lives (" + state.lives() + ") — buying " + hpot.name() + " to stay alive.");
    }

    Optional<Decision> upgrade = upgradePurchase(state, shop, config);
    if (upgrade.isPresent()) {
      return upgrade.get();
    }

    List<Ranked> ranked = rank(ads);
    if (ranked.isEmpty()) {
      // Nothing to solve at all: a potion is the only move left that buys a fresh board.
      return hpot != null && state.gold() >= hpot.cost()
          ? Decision.buy(hpot, "No solvable ads — buying " + hpot.name() + " to re-roll the board.")
          : Decision.stop("No solvable ads and no affordable purchase to re-roll.");
    }

    Optional<Ranked> worthAttempting = bestWorthAttempting(ranked, config.safeProbability());
    if (worthAttempting.isPresent()) {
      return solveDecision(worthAttempting.get(), "cheapest safe quest");
    }

    return solveDecision(
        safest(ranked), "forced gamble — nothing on the board clears the safety floor");
  }

  /**
   * The surplus upgrade. One trigger — gold piled up past the dump threshold — and the purchase
   * must still leave the potion reserve intact. Below that the dragon waits rather than spends.
   */
  private static Optional<Decision> upgradePurchase(
      GameState state, List<ShopItem> shop, StrategyConfig config) {
    if (state.gold() <= config.goldDumpThreshold()) {
      return Optional.empty();
    }
    // Spending down to the reserve, not to zero: cost <= gold - reserve.
    ShopItem upgrade = cheapestUpgrade(shop, state.gold() - config.upgradeGoldReserve());
    if (upgrade == null) {
      return Optional.empty();
    }
    return Optional.of(Decision.buy(upgrade,
        "Gold above " + config.goldDumpThreshold() + " — turning the surplus into a level: buying "
            + upgrade.name() + "."));
  }

  /**
   * The decision for an ad the user picked by hand. The engine still owns the valuation — a manual
   * solve is scored the same way an automatic one is, so the two cannot drift apart.
   */
  public Decision manualSolve(Ad ad) {
    Ranked r = rankedAd(ad,
        ProbabilityTier.fromDisplay(ad.probability())
            .map(tier -> probabilities.successProbability(tier, ad.reward()))
            .orElseGet(() -> probabilities.successProbability(ad.probability())));
    return Decision.solve(r.ad(), r.probability(), r.expectedValue(), "Manual solve requested by user.");
  }

  /** Survival comes first: top up while a potion is both needed and affordable. */
  private static boolean canHeal(GameState state, ShopItem hpot, StrategyConfig config) {
    return state.lives() <= config.healThreshold() && hpot != null && state.gold() >= hpot.cost();
  }

  /**
   * The ads worth considering at all, each paired with its measured odds and expected value. An ad
   * is out if its tier is one this build doesn't know, if it has expired, or if it pays nothing.
   */
  private List<Ranked> rank(List<Ad> ads) {
    return ads.stream()
        .filter(ad -> ad.expiresIn() > 0 && ad.reward() > 0)
        .flatMap(ad -> ProbabilityTier.fromDisplay(ad.probability()).stream()
            .map(tier -> rankedAd(ad, probabilities.successProbability(tier, ad.reward()))))
        .toList();
  }

  private static Ranked rankedAd(Ad ad, double probability) {
    return new Ranked(ad, probability, ad.reward() * probability);
  }

  /** The cheapest ad at or above the probability floor, if any clears it. */
  private static Optional<Ranked> bestWorthAttempting(List<Ranked> ranked, double floor) {
    return ranked.stream().filter(r -> r.probability() >= floor).min(BY_LOWEST_REWARD);
  }

  /** The best odds on the board regardless of pay — the least-bad forced gamble. */
  private static Ranked safest(List<Ranked> ranked) {
    return ranked.stream().min(BY_SAFEST).orElseThrow();
  }

  private record Ranked(Ad ad, double probability, double expectedValue) {}

  /** Cheapest first, then adId so equal-reward boards resolve deterministically. */
  private static final Comparator<Ranked> BY_LOWEST_REWARD =
      Comparator.comparingInt((Ranked r) -> r.ad().reward())
          .thenComparing(r -> r.ad().adId());

  /**
   * Best odds first, then the <em>cheaper</em> ad, then adId so boards resolve deterministically.
   * Cheap is not a tiebreaker of taste: within one tier a dearer ad is measurably likelier to fail,
   * which is what the reward ceilings in {@code probabilities.json} record. An earlier version broke
   * ties toward the bigger purse on the assumption that equal labels meant equal risk; they do not.
   */
  private static final Comparator<Ranked> BY_SAFEST =
      Comparator.comparingDouble(Ranked::probability).reversed()
          .thenComparing(r -> r.ad().reward())
          .thenComparing(r -> r.ad().adId());

  private static ShopItem findItem(List<ShopItem> shop, String id) {
    return shop.stream().filter(i -> i.id().equals(id)).findFirst().orElse(null);
  }

  /** @param budget the most this purchase may cost — gold already net of any reserve. */
  private static ShopItem cheapestUpgrade(List<ShopItem> shop, int budget) {
    return shop.stream()
        .filter(i -> !i.id().equals(HEALING_POTION_ID) && i.cost() <= budget)
        .min(Comparator.comparingInt(ShopItem::cost))
        .orElse(null);
  }

  private static Decision solveDecision(Ranked r, String why) {
    String reason =
        "Solving \"" + truncate(r.ad().message()) + "\" [" + r.ad().probability() + "] — " + why
            + " (reward " + r.ad().reward() + ", EV " + Math.round(r.expectedValue()) + ").";
    return Decision.solve(r.ad(), r.probability(), r.expectedValue(), reason);
  }

  /** Keeps a decision reason to one readable line in the UI log. */
  private static final int MAX_REASON_MESSAGE_CHARS = 48;

  private static String truncate(String text) {
    return text.length() > MAX_REASON_MESSAGE_CHARS
        ? text.substring(0, MAX_REASON_MESSAGE_CHARS - 1) + "…"
        : text;
  }
}
