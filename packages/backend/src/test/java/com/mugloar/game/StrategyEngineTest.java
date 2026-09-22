package com.mugloar.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mugloar.domain.DecisionType;
import com.mugloar.domain.Ad;
import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StrategyEngineTest {

  private static StrategyEngine engine;

  private final List<ShopItem> shop = List.of(
      new ShopItem("hpot", "Healing potion", 50),
      new ShopItem("cs", "Claw Sharpening", 100),
      new ShopItem("ch", "Claw Honing", 300));

  @BeforeAll
  static void setup() {
    engine = new StrategyEngine(new Probabilities(JsonMapper.builder().build(), "/test-probabilities.json"));
  }

  private static GameState state(int lives, int gold) {
    return new GameState("g1", lives, gold, 0, 0, 0, 0);
  }

  private static GameState state(int lives, int gold, int score) {
    return new GameState("g1", lives, gold, 0, score, 0, 0);
  }

  private static Ad ad(String id, String prob, int reward) {
    return new Ad(id, "Do a thing", reward, 5, null, prob);
  }

  private static Ad ad(String id, String prob, int reward, int expiresIn) {
    return new Ad(id, "Do a thing", reward, expiresIn, null, prob);
  }

  @Test
  void stopsWhenDead() {
    assertEquals(DecisionType.STOP, engine.decide(state(0, 100), List.of(ad("a", "Sure thing", 10)), shop).type());
  }

  @Test
  void keepsPlayingPastGoal() {
    var d = engine.decide(state(3, 0, 1500), List.of(ad("a", "Sure thing", 40)), shop);
    assertEquals(DecisionType.SOLVE, d.type());
  }

  @Test
  void healsWhenLivesLow() {
    var d = engine.decide(state(2, 50), List.of(ad("a", "Sure thing", 99)), shop);
    assertEquals(DecisionType.BUY, d.type());
    assertEquals("hpot", d.itemId());
  }

  @Test
  void fallsBackToSafestWhenHealUnaffordable() {
    assertEquals(DecisionType.SOLVE, engine.decide(state(2, 10), List.of(ad("a", "Sure thing", 10)), shop).type());
  }

  @Test
  void picksCheapestSafeQuest() {
    var ads = List.of(ad("low", "Sure thing", 10), ad("high", "Piece of cake", 80), ad("mid", "Quite likely", 40));
    var d = engine.decide(state(3, 0), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("low", d.adId());
  }

  @Test
  void ignoresExpiredAndUnknownTiers() {
    var ads = List.of(
        ad("expired", "Sure thing", 999, 0),
        ad("weird", "Totally made up", 999),
        ad("valid", "Sure thing", 20));
    assertEquals("valid", engine.decide(state(3, 0), ads, shop).adId());
  }

  /**
   * Below the safety floor there is nothing left to optimise for: every option costs a life at
   * some rate, so the forced gamble takes the best odds, not the biggest purse. "big" wins on
   * expected value (200 x 0.35 = 70 against 20 x 0.55 = 11) and still loses.
   */
  @Test
  void takesTheSafestAdWhenNoSafeQuest() {
    var ads = List.of(ad("big", "Risky", 200), ad("safer", "Gamble", 20));
    var d = engine.decide(state(3, 0), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("safer", d.adId());
  }

  /**
   * Equal odds below the floor: take the cheaper one. The risk is <em>not</em> the same either way —
   * within a tier a dearer ad fails more often, which is what the reward ceilings measure.
   */
  @Test
  void breaksSafestTiesTowardTheCheaperAd() {
    var ads = List.of(ad("poor", "Hmmm....", 20), ad("rich", "Gamble", 200));
    var d = engine.decide(state(3, 0), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("poor", d.adId());
  }

  // --- reward ceilings ------------------------------------------------------

  /**
   * The headline finding: a "Sure thing" at or above 130 gold is worth 0.09, not the 0.9 its label
   * claims, so the unconditional safety floor rejects it with no extra rule in decide().
   */
  @Test
  void refusesASureThingPricedAboveItsCeiling() {
    var ads = List.of(ad("dear", "Sure thing", 200), ad("gamble", "Gamble", 20));
    var d = engine.decide(state(3, 0), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("gamble", d.adId(), "the 0.55 Gamble outranks a 0.09 Sure thing");
  }

  /** Below the ceiling the label still stands — the rule must not punish cheap safe quests. */
  @Test
  void stillTakesASureThingUnderItsCeiling() {
    var ads = List.of(ad("fine", "Sure thing", 129), ad("gamble", "Gamble", 20));
    var d = engine.decide(state(3, 100), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("fine", d.adId());
  }

  /** Each tier carries its own cut: 140 is past Sure thing's 130 but short of Piece of cake's 150. */
  @Test
  void appliesEachTiersOwnCeiling() {
    var ads = List.of(ad("sure", "Sure thing", 140), ad("cake", "Piece of cake", 140));
    var d = engine.decide(state(3, 100), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("cake", d.adId(), "Piece of cake is still trusted at 140; Sure thing is not");
  }

  /** A tier with no ceiling entry is trusted at any price — the data says these are flat. */
  @Test
  void leavesTiersWithoutACeilingAlone() {
    var ads = List.of(ad("dear", "Walk in the park", 900));
    var d = engine.decide(state(3, 0), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("dear", d.adId());
  }

  /**
   * Gold is for staying alive. A bad board is paid for with a risk, not with an upgrade bought to
   * re-roll it — that purchase is what used to drain the potion fund.
   */
  @Test
  void gamblesRatherThanBuyingAnUpgradeToReroll() {
    var d = engine.decide(state(3, 100), List.of(ad("deadly", "Suicide mission", 500)), shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("deadly", d.adId());
  }

  /**
   * The upgrade rule is pinned with an explicit config whose threshold sits well clear of
   * {@code reserve + cheapest upgrade}, so a failure here points at the rule rather than at the
   * shipped numbers. DEFAULT is pinned separately below.
   */
  private static final StrategyConfig HIGH_DUMP = new StrategyConfig(2, 0.7, 300, 800);

  /** Surplus gold is worth more as a level than as a hoard. */
  @Test
  void dumpsSurplusGoldIntoAnUpgrade() {
    var ads = List.of(ad("safe", "Sure thing", 40));
    var d = engine.decide(state(3, 850), ads, shop, HIGH_DUMP);
    assertEquals(DecisionType.BUY, d.type());
    assertEquals("cs", d.itemId());
  }

  /** Rich enough to afford an upgrade, but not past the threshold: no reason to buy one. */
  @Test
  void doesNotBuyUpgradesBelowTheThreshold() {
    var ads = List.of(ad("safe", "Sure thing", 40));
    var d = engine.decide(state(3, 700), ads, shop, HIGH_DUMP);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("safe", d.adId());
  }

  /**
   * The reserve can veto a purchase the threshold allows: 360 clears a 350 dump but leaves only 60
   * above the 300 reserve, short of the 100 upgrade. This is why the shipped threshold is 400.
   */
  @Test
  void waitsWhenTheUpgradeWouldEatTheReserve() {
    var ads = List.of(ad("safe", "Sure thing", 40));
    var d = engine.decide(state(3, 360), ads, shop, new StrategyConfig(2, 0.7, 300, 350));
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("safe", d.adId());
  }

  /**
   * The shipped tuning puts the first upgrade just past 400, because the cheapest one costs 100 and
   * the purchase has to leave the 300-gold potion reserve behind. At 400 exactly nothing is spare.
   */
  @Test
  void defaultTuningBuysTheFirstUpgradeJustOverFourHundred() {
    var ads = List.of(ad("safe", "Sure thing", 40));
    assertEquals(DecisionType.SOLVE, engine.decide(state(3, 400), ads, shop).type());
    var d = engine.decide(state(3, 401), ads, shop);
    assertEquals(DecisionType.BUY, d.type());
    assertEquals("cs", d.itemId());
  }

  /** Well below the threshold the default tuning keeps solving. */
  @Test
  void defaultTuningWaitsBelowTheThreshold() {
    var d = engine.decide(state(3, 300), List.of(ad("safe", "Sure thing", 40)), shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("safe", d.adId());
  }

  /** Survival outranks the upgrade. */
  @Test
  void healsBeforeBuyingAnUpgrade() {
    var d = engine.decide(state(2, 900), List.of(ad("safe", "Sure thing", 40)), shop);
    assertEquals(DecisionType.BUY, d.type());
    assertEquals("hpot", d.itemId());
  }

  @Test
  void healsToRerollWhenOnlyDeadlyAndLowLives() {
    var d = engine.decide(state(1, 50), List.of(ad("deadly", "Impossible", 500)), shop);
    assertEquals(DecisionType.BUY, d.type());
    assertEquals("hpot", d.itemId());
  }

  @Test
  void forcedGambleWhenBrokeAndCornered() {
    var d = engine.decide(state(3, 0), List.of(ad("deadly", "Suicide mission", 500)), shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("deadly", d.adId());
  }

  @Test
  void stopsWhenNoSolvableAdsAndBroke() {
    assertEquals(DecisionType.STOP, engine.decide(state(3, 0), List.of(), shop).type());
  }

  /**
   * Raw reward decides among safe ads, not expected value. "rich" is the better bet on expected
   * value (100 x 0.8 = 80 vs 95 x 0.9 = 85.5 — close) but what settles it is that it costs more.
   */
  @Test
  void prefersLowerRewardAmongSafeQuests() {
    var ads = List.of(ad("rich", "Walk in the park", 100), ad("steady", "Sure thing", 95));
    var d = engine.decide(state(3, 100), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("steady", d.adId());
  }

  /** Lives and gold to spare is no reason to gamble: below the safe floor is off the table. */
  @Test
  void refusesGambleEvenWhenLivesAndGoldAreHealthy() {
    var ads = List.of(ad("steady", "Sure thing", 100), ad("gamble", "Gamble", 200));
    var d = engine.decide(state(3, 100), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("steady", d.adId());
  }

  /** The safe floor holds at the heal threshold too: EV 275 loses to a certainty worth 18. */
  @Test
  void refusesHighEvGambleWhenLivesLowAndPotionUnaffordable() {
    var ads = List.of(ad("gamble", "Gamble", 500), ad("steady", "Sure thing", 20));
    var d = engine.decide(state(2, 10), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("steady", d.adId());
  }

  /**
   * A safe ad is taken even when it is the dearer one, if it is the only thing clearing the floor —
   * but only up to its ceiling. This used to price "steady" at 500, which the pooled measurements
   * later showed is a ~9% bet, not a certainty; at 120 the label still holds.
   */
  @Test
  void takesTheOnlySafeQuestEvenWhenItIsTheDearest() {
    var ads = List.of(ad("gamble", "Gamble", 20), ad("steady", "Sure thing", 120));
    var d = engine.decide(state(4, 200), ads, shop);
    assertEquals(DecisionType.SOLVE, d.type());
    assertEquals("steady", d.adId());
  }
}
