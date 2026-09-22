package com.mugloar.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mugloar.domain.Ad;
import com.mugloar.domain.ProbabilityTier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The pair selection behind {@code characterize:expiry}. A live run costs the better part of an
 * hour and yields nothing if the arms are drawn wrongly, so the choice is a pure function checked
 * offline — the same bargain {@link AttemptAnalysisTest} makes for the arithmetic.
 */
class ExpiryPairingTest {

  private static Ad ad(String id, ProbabilityTier tier, int expiresIn, int reward) {
    return new Ad(id, "Do a thing", reward, expiresIn, null, tier.display());
  }

  private static final ProbabilityTier SURE = ProbabilityTier.SURE_THING;
  private static final ProbabilityTier LIKELY = ProbabilityTier.QUITE_LIKELY;

  // --- choosing the tier to draw the pair from ------------------------------

  @Test
  void pairsTheTierHoldingBothAnExpiringAndAFreshAd() {
    var board = List.of(ad("dying", SURE, 1, 10), ad("fresh", SURE, 5, 10));
    assertEquals(SURE, Experiments.pairableTier(board, Map.of()));
  }

  @Test
  void refusesATierThatOnlyOffersOneSideOfThePair() {
    assertNull(Experiments.pairableTier(List.of(ad("dying", SURE, 1, 10)), Map.of()));
    assertNull(Experiments.pairableTier(List.of(ad("fresh", SURE, 5, 10)), Map.of()));
  }

  /** The pair must straddle the gap: a 2-turn ad is neither expiring nor fresh, so it pairs with nothing. */
  @Test
  void treatsTheMiddleOfTheBoardAsNeitherArm() {
    var board = List.of(ad("mid", SURE, 2, 10), ad("alsoMid", SURE, 2, 10));
    assertNull(Experiments.pairableTier(board, Map.of()));

    var straddling = List.of(ad("mid", SURE, 2, 10), ad("dying", SURE, 1, 10), ad("fresh", SURE, 3, 10));
    assertEquals(SURE, Experiments.pairableTier(straddling, Map.of()));
  }

  @Test
  void refusesToPairAcrossDifferentTiers() {
    var board = List.of(ad("dying", SURE, 1, 10), ad("fresh", LIKELY, 5, 10));
    assertNull(Experiments.pairableTier(board, Map.of()));
  }

  /** Balances the run: among tiers that can pair, the one sampled least so far wins. */
  @Test
  void prefersTheLeastSampledPairableTier() {
    var board = List.of(
        ad("sureDying", SURE, 1, 10), ad("sureFresh", SURE, 5, 10),
        ad("likelyDying", LIKELY, 1, 10), ad("likelyFresh", LIKELY, 5, 10));
    assertEquals(LIKELY, Experiments.pairableTier(board, Map.of(SURE, 40, LIKELY, 3)));
    assertEquals(SURE, Experiments.pairableTier(board, Map.of(SURE, 3, LIKELY, 40)));
  }

  /** An unsampled tier counts as zero, not as missing. */
  @Test
  void treatsAnAbsentCountAsUnsampled() {
    var board = List.of(
        ad("sureDying", SURE, 1, 10), ad("sureFresh", SURE, 5, 10),
        ad("likelyDying", LIKELY, 1, 10), ad("likelyFresh", LIKELY, 5, 10));
    assertEquals(LIKELY, Experiments.pairableTier(board, Map.of(SURE, 12)));
  }

  /** Equal counts resolve by tier order, so a run is reproducible from its seed alone. */
  @Test
  void breaksCountTiesDeterministically() {
    var board = List.of(
        ad("sureDying", SURE, 1, 10), ad("sureFresh", SURE, 5, 10),
        ad("likelyDying", LIKELY, 1, 10), ad("likelyFresh", LIKELY, 5, 10));
    var counts = Map.of(SURE, 7, LIKELY, 7);
    assertEquals(Experiments.pairableTier(board, counts), Experiments.pairableTier(board, counts));
    assertEquals(SURE, Experiments.pairableTier(board, counts));
  }

  // --- choosing the ad within the tier --------------------------------------

  @Test
  void expiringArmTakesTheAdClosestToExpiry() {
    var board = List.of(ad("two", SURE, 2, 10), ad("one", SURE, 1, 10), ad("five", SURE, 5, 10));
    assertEquals("one", Experiments.expiringAd(board, SURE).adId());
  }

  @Test
  void freshArmTakesTheAdFurthestFromExpiry() {
    var board = List.of(ad("three", SURE, 3, 10), ad("seven", SURE, 7, 10), ad("one", SURE, 1, 10));
    assertEquals("seven", Experiments.freshAd(board, SURE).adId());
  }

  /** Equal expiry resolves by adId, so the same board always yields the same attempt. */
  @Test
  void breaksAdTiesByIdSoABoardReplaysIdentically() {
    var board = List.of(ad("zulu", SURE, 1, 10), ad("alpha", SURE, 1, 99));
    assertEquals("alpha", Experiments.expiringAd(board, SURE).adId());
  }

  @Test
  void ignoresAdsOfOtherTiersWhenPickingEachArm() {
    var board = List.of(
        ad("wrongTier", LIKELY, 1, 10),
        ad("right", SURE, 1, 10),
        ad("wrongTierFresh", LIKELY, 9, 10),
        ad("rightFresh", SURE, 4, 10));
    assertEquals("right", Experiments.expiringAd(board, SURE).adId());
    assertEquals("rightFresh", Experiments.freshAd(board, SURE).adId());
  }
}
