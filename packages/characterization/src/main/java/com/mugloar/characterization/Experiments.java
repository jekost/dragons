package com.mugloar.characterization;

import static java.util.Comparator.comparingInt;

import com.mugloar.client.GameApiClient;
import com.mugloar.domain.Ad;
import com.mugloar.domain.BuyResponse;
import com.mugloar.domain.Decision;
import com.mugloar.domain.GameState;
import com.mugloar.domain.ProbabilityTier;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResponse;
import com.mugloar.game.StrategyEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntPredicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Live-API experiments that measure the game's undocumented mechanics. */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class Experiments {

  /** A tier needs this many attempts before its measured rate replaces the carried-forward prior. */
  static final int MIN_SAMPLES = 15;

  /**
   * The tiers the level experiment spends its whole budget on. Success sits near 0.5 here, where a
   * shift is actually visible; "Sure thing" at 0.98 and "Suicide mission" at 0.17 have no headroom
   * to show an effect either way, so sampling them would burn turns for nothing.
   */
  static final List<ProbabilityTier> MID_TIERS = List.of(
      ProbabilityTier.QUITE_LIKELY,
      ProbabilityTier.HMMM,
      ProbabilityTier.GAMBLE,
      ProbabilityTier.RISKY,
      ProbabilityTier.RATHER_DETRIMENTAL);

  private final GameApiClient client;

  record ShopStability(int games, int distinctLayouts, Map<String, Integer> layoutCounts) {}

  /**
   * Which upgrade policy a game was assigned. Level normally rises with the turn counter, so
   * observational data cannot tell "levelling helps" apart from "later turns differ". Assigning the
   * policy per game breaks that link: level becomes something we set, not something the game earns.
   */
  enum UpgradeArm {
    /** Never buy an upgrade. Level stays where the game started; potions are still allowed. */
    NONE,
    /** Buy the cheapest affordable upgrade at every opportunity. */
    FAST
  }

  /**
   * Which ad of the target tier was taken. Randomised per turn rather than per game: every turn is
   * then its own coin flip, which gives far more power than four games per cell, and it balances
   * tiers automatically. {@link #NA} marks a turn where the board offered no choice to randomise.
   */
  enum RewardArm {
    /** The highest-reward ad of the target tier. */
    HIGH,
    /** The lowest-reward ad of the same tier — the paired control. */
    LOW,
    /** No pair available; the attempt is logged but is not part of the contrast. */
    NA
  }

  /**
   * Which side of the expiry pair was taken. Randomised per turn, like {@link RewardArm}, and for
   * the same reason: the contrast is drawn within one tier on one board, so the turn is the trial.
   */
  enum ExpiryArm {
    /** The ad on its last turn — the thing under test. */
    EXPIRING,
    /** An ad of the same tier with turns to spare — the paired control. */
    FRESH,
    /** No tier offered both; the attempt is logged but is not part of the contrast. */
    NA
  }

  /** One solve, with the game state as it stood *before* the attempt. One row of attempts.csv. */
  record Attempt(
      int game,
      UpgradeArm arm,
      RewardArm rewardArm,
      ExpiryArm expiryArm,
      int turn,
      int level,
      int lives,
      int gold,
      int score,
      ProbabilityTier tier,
      String adId,
      String message,
      int reward,
      int expiresIn,
      boolean success) {}

  /**
   * One ad as it appeared on the board, whether or not it was taken. Logging the whole board is what
   * makes reward-vs-level answerable at all: the attempt log only ever holds ads the picker chose,
   * which is a biased sample of what was on offer.
   */
  record BoardRow(
      int game,
      int turn,
      int level,
      UpgradeArm arm,
      String adId,
      ProbabilityTier tier,
      int reward,
      int expiresIn,
      Integer encrypted,
      boolean chosen) {}

  record LevelResult(List<Attempt> attempts, int games, List<Integer> finals) {}

  record RewardResult(List<Attempt> attempts, List<BoardRow> board, int games, long seed, List<Integer> finals) {}

  record ExpiryResult(
      List<Attempt> attempts, List<BoardRow> board, int games, long seed, List<Integer> finals) {}

  /**
   * Which valuation the solver ran with. The reward ceilings live in data, not code, so they are
   * the one recent change that can be A/B'd honestly: both arms are the same engine, the same
   * config and the same board, differing only in whether a dear ad is priced at its label.
   */
  enum ValuationArm {
    /** The shipped valuation: a tier above its reward ceiling is priced at the measured rate. */
    CEILINGS,
    /** The previous behaviour: the label alone, whatever the ad costs. */
    LABEL_ONLY
  }

  /** One finished benchmark game. {@code turns} counts decisions executed, not API calls. */
  record BenchGame(ValuationArm arm, int score, int turns, boolean died, String ending) {}

  record BenchmarkResult(List<BenchGame> games, int turnCap) {}

  /** Start many games and compare shop layouts to prove prices/inventory are (or aren't) fixed. */
  ShopStability runShopStability(int games) {
    Map<String, Integer> layouts = new LinkedHashMap<>();
    for (int i = 0; i < games; i++) {
      GameState start = client.startGame();
      List<ShopItem> shop = client.getShop(start.gameId());
      String signature = shop.stream()
          .map(it -> it.id() + ":" + it.cost())
          .sorted()
          .reduce((a, b) -> a + "," + b)
          .orElse("");
      layouts.merge(signature, 1, Integer::sum);
      log.info("    [%2d/%d] distinct layouts so far: %d".formatted(i + 1, games, layouts.size()));
    }
    return new ShopStability(games, layouts.size(), layouts);
  }

  record Cell(int success, int total) {}

  record SamplingResult(Map<ProbabilityTier, Cell> byTier, int games, int wins, List<Integer> finals) {}

  /**
   * Play many games, logging (tier, success) for every attempt — but ONLY for "fresh" ads
   * (expiresIn >= freshThreshold), which controls for the expiry confound. Rotating the target
   * tier per game spreads coverage.
   *
   * <p>This is a measurement run, not a score run: it deliberately attempts deadly tiers even at
   * one life and plays on past 1000 points. A lost game is not a wasted game — every attempt it
   * logged before dying is a data point, and refusing risk is exactly what starved the dangerous
   * tiers of samples before.
   */
  SamplingResult runProbabilitySampling(int games, int turnCap, int freshThreshold) {
    Map<ProbabilityTier, int[]> stats = new EnumMap<>(ProbabilityTier.class);
    List<Integer> finals = new ArrayList<>();
    int wins = 0;

    for (int gi = 0; gi < games; gi++) {
      GameState start = client.startGame();
      String gameId = start.gameId();
      RunState state = new RunState(start);
      List<ShopItem> shop = client.getShop(gameId);
      ProbabilityTier target = ProbabilityTier.values()[gi % ProbabilityTier.values().length];

      // Upgrade on every sixth turn: enough to move level over a run without dominating the
      // sample, since this experiment is about tiers rather than levels.
      GameOutcome outcome = playGame(gameId, state, shop, turnCap, freshThreshold,
          turn -> turn % 6 == 0 && state.level < 6,
          (turn, fresh) -> {
            Ad pick = pickForSampling(fresh, target, stats);
            SolveResponse r = client.solve(gameId, pick.adId());
            record(stats, tierOf(pick), r.success());
            return r;
          });

      finals.add(state.score);
      if (state.score >= StrategyEngine.GOAL_SCORE) {
        wins++;
      }
      log.info("    [%2d/%d] target %-19s %3d attempts  score %6d  %-18s  tiers ready %2d/%d".formatted(
          gi + 1, games, target.display(), outcome.attempted(), state.score, outcome.ending(),
          tiersReady(stats), ProbabilityTier.values().length));
    }

    Map<ProbabilityTier, Cell> byTier = new EnumMap<>(ProbabilityTier.class);
    stats.forEach((tier, sc) -> byTier.put(tier, new Cell(sc[0], sc[1])));
    return new SamplingResult(byTier, games, wins, finals);
  }

  /**
   * Does the dragon's level change its odds, or does the game scale difficulty to match?
   *
   * <p>Each game is assigned both a target tier (round-robin over {@link #MID_TIERS}) and an
   * {@link UpgradeArm}, so that over a full run every (tier, arm) pair gets the same number of
   * games. Because the arm is assigned rather than earned, comparing arms at a matched turn range
   * isolates the level effect, and looking at turn number *within* arm {@code NONE} — where level
   * never moves — isolates any difficulty scaling.
   *
   * <p>Every attempt is logged in full; the analysis happens afterwards over the rows, so a run
   * never has to be repeated just because a different question came up.
   */
  LevelResult runLevelExperiment(int games, int turnCap, int freshThreshold) {
    List<Attempt> attempts = new ArrayList<>();
    List<Integer> finals = new ArrayList<>();
    Map<ProbabilityTier, int[]> midCounts = new EnumMap<>(ProbabilityTier.class);

    for (int gi = 0; gi < games; gi++) {
      ProbabilityTier target = MID_TIERS.get(gi % MID_TIERS.size());
      UpgradeArm arm = UpgradeArm.values()[(gi / MID_TIERS.size()) % UpgradeArm.values().length];

      GameState start = client.startGame();
      String gameId = start.gameId();
      RunState state = new RunState(start);
      List<ShopItem> shop = client.getShop(gameId);
      int game = gi;

      // Potions are allowed in both arms: they keep the two arms alive for comparable lengths
      // without touching level, which is the one variable under test.
      GameOutcome outcome = playGame(gameId, state, shop, turnCap, freshThreshold,
          turn -> arm == UpgradeArm.FAST && state.level < 6,
          (turn, fresh) -> {
            Ad pick = pickForLevelExperiment(fresh, target, midCounts);
            ProbabilityTier tier = tierOf(pick);
            SolveResponse r = client.solve(gameId, pick.adId());
            // State is captured as it stood before the solve — the state the attempt faced.
            attempts.add(new Attempt(game, arm, RewardArm.NA, ExpiryArm.NA, turn, state.level, state.lives,
                state.gold, state.score, tier, pick.adId(), pick.message(), pick.reward(),
                pick.expiresIn(), r.success()));
            if (MID_TIERS.contains(tier)) {
              midCounts.computeIfAbsent(tier, t -> new int[1])[0]++;
            }
            return r;
          });

      finals.add(state.score);
      log.info("    [%2d/%d] %-4s target %-19s %3d attempts  level %d  score %6d  %s".formatted(
          gi + 1, games, arm, target.display(), outcome.attempted(), state.level, state.score,
          outcome.ending()));
    }

    return new LevelResult(attempts, games, finals);
  }

  /**
   * Is an ad's difficulty tied to its prize money, and do upgrades buy bigger prizes?
   *
   * <p>Two questions, one run, because both need the same thing the earlier runs never recorded:
   * the whole board, not just the ad that was taken.
   *
   * <p><b>Difficulty vs reward.</b> Each turn the board is grouped by tier; the mid tier offering
   * the widest spread between its cheapest and dearest ad is picked, and a coin flip decides which
   * end to attempt. Both ads carry the *same label*, so any difference in success is the money and
   * nothing else. Randomising per turn rather than per game keeps tiers balanced and turns every
   * turn into an independent trial.
   *
   * <p><b>Reward vs level.</b> Games alternate {@link UpgradeArm}s, and every ad on every board is
   * logged with the level that saw it. Rewards are already known to climb steeply with the turn
   * counter, so the comparison that matters is level-against-level *within* a turn window — which
   * the board log supports and the attempt log never could.
   */
  RewardResult runRewardExperiment(int games, int turnCap, int freshThreshold, long seed) {
    List<Attempt> attempts = new ArrayList<>();
    List<BoardRow> board = new ArrayList<>();
    List<Integer> finals = new ArrayList<>();
    Random random = new Random(seed);

    for (int gi = 0; gi < games; gi++) {
      UpgradeArm arm = UpgradeArm.values()[gi % UpgradeArm.values().length];

      GameState start = client.startGame();
      String gameId = start.gameId();
      RunState state = new RunState(start);
      List<ShopItem> shop = client.getShop(gameId);
      int game = gi;
      // Counts turns that actually paired. Not every turn does, and only paired ones are evidence.
      int[] pairedTurns = {0};

      GameOutcome outcome = playGame(gameId, state, shop, turnCap, freshThreshold,
          turn -> arm == UpgradeArm.FAST && state.level < 6,
          (turn, fresh) -> {
            ArmedPick chosen = chooseRewardArm(fresh, random);
            if (chosen.arm() != RewardArm.NA) {
              pairedTurns[0]++;
            }
            logBoard(board, game, turn, state.level, arm, fresh, chosen.ad());

            Ad pick = chosen.ad();
            SolveResponse r = client.solve(gameId, pick.adId());
            attempts.add(new Attempt(game, arm, chosen.arm(), ExpiryArm.NA, turn, state.level, state.lives,
                state.gold, state.score, tierOf(pick), pick.adId(), pick.message(), pick.reward(),
                pick.expiresIn(), r.success()));
            return r;
          });

      finals.add(state.score);
      log.info("    [%2d/%d] %-4s %3d paired turns  level %d  score %6d  %s".formatted(
          gi + 1, games, arm, pairedTurns[0], state.level, state.score, outcome.ending()));
    }

    return new RewardResult(attempts, board, games, seed, finals);
  }

  /** Every live ad. {@code playGame} filters the board at this threshold, so nothing is dropped. */
  private static final int WHOLE_BOARD = 1;

  /** An ad on its last turn: this is the final board it appears on. */
  static final int EXPIRING_AT = 1;

  /** Turns to spare — and the threshold probabilities.json was measured at, so it is the baseline. */
  static final int FRESH_AT = 3;

  /**
   * Does an ad about to expire succeed at the rate its tier label advertises? The tier labels were
   * only ever measured on ads with {@link #FRESH_AT}+ turns left, so the answer is extrapolation
   * everywhere else — and the rival solver we compared against refuses near-expiry ads outright, on
   * reasoning that does not apply to a bot that solves the instant it decides.
   *
   * <p>Level is pinned at 0 (no upgrades) because levelling has its own experiment; holding it
   * still removes a variable rather than re-measuring it here. The contrast is drawn <em>within</em>
   * one tier on one board, so a gap between the arms is the expiry alone.
   */
  ExpiryResult runExpiryExperiment(int games, int turnCap, long seed) {
    List<Attempt> attempts = new ArrayList<>();
    List<BoardRow> board = new ArrayList<>();
    List<Integer> finals = new ArrayList<>();
    Random random = new Random(seed);
    Map<ProbabilityTier, Integer> sampled = new EnumMap<>(ProbabilityTier.class);

    for (int gi = 0; gi < games; gi++) {
      GameState start = client.startGame();
      String gameId = start.gameId();
      RunState state = new RunState(start);
      List<ShopItem> shop = client.getShop(gameId);
      int game = gi;
      // An ad needs six unsolved turns to reach its last one, so early turns cannot pair.
      int[] pairedTurns = {0};

      // freshThreshold 1: the whole board, expiring ads included. That is the point of this run.
      GameOutcome outcome = playGame(gameId, state, shop, turnCap, WHOLE_BOARD,
          turn -> false,
          (turn, all) -> {
            ProbabilityTier target = pairableTier(all, sampled);
            ExpiryArm arm = target == null
                ? ExpiryArm.NA
                : (random.nextBoolean() ? ExpiryArm.EXPIRING : ExpiryArm.FRESH);
            Ad pick = switch (arm) {
              case EXPIRING -> expiringAd(all, target);
              case FRESH -> freshAd(all, target);
              case NA -> safestAd(all);
            };
            if (arm != ExpiryArm.NA) {
              pairedTurns[0]++;
              sampled.merge(target, 1, Integer::sum);
            }
            logBoard(board, game, turn, state.level, UpgradeArm.NONE, all, pick);

            SolveResponse r = client.solve(gameId, pick.adId());
            attempts.add(new Attempt(game, UpgradeArm.NONE, RewardArm.NA, arm, turn, state.level,
                state.lives, state.gold, state.score, tierOf(pick), pick.adId(), pick.message(),
                pick.reward(), pick.expiresIn(), r.success()));
            return r;
          });

      finals.add(state.score);
      log.info("    [%2d/%d] %3d paired turns  level %d  score %6d  %s".formatted(
          gi + 1, games, pairedTurns[0], state.level, state.score, outcome.ending()));
    }

    return new ExpiryResult(attempts, board, games, seed, finals);
  }

  /**
   * Plays whole games through the real {@link StrategyEngine}, alternating valuation arms, to ask
   * whether the reward ceilings actually buy score. Unlike every other run here this one is a
   * <em>score</em> run: it drives {@code decide()} and obeys it, so the numbers mean something
   * about the solver rather than about the game.
   *
   * <p>The turn loop mirrors {@code GameService}: decide, execute, re-sync from the response. Arms
   * alternate game by game so both meet the same upstream conditions.
   */
  BenchmarkResult runSolverBenchmark(
      int gamesPerArm, int turnCap, Map<ValuationArm, StrategyEngine> engines) {
    List<BenchGame> played = new ArrayList<>();
    ValuationArm[] arms = ValuationArm.values();

    for (int round = 0; round < gamesPerArm; round++) {
      for (ValuationArm arm : arms) {
        BenchGame game = playBenchmarkGame(arm, engines.get(arm), turnCap);
        played.add(game);
        log.info("    [%2d/%d] %-10s score %6d  turns %3d  %s".formatted(
            round + 1, gamesPerArm, arm, game.score(), game.turns(), game.ending()));
      }
    }
    return new BenchmarkResult(played, turnCap);
  }

  /** One game, played to death or to the cap, obeying the engine at every turn. */
  private BenchGame playBenchmarkGame(ValuationArm arm, StrategyEngine engine, int turnCap) {
    GameState state = client.startGame();
    String gameId = state.gameId();
    List<ShopItem> shop = client.getShop(gameId);

    for (int turn = 0; turn < turnCap; turn++) {
      // A dead game's endpoints return 410, so never ask it for a board.
      if (state.lives() <= 0) {
        return new BenchGame(arm, state.score(), turn, true, "died on turn " + turn);
      }
      Decision decision = engine.decide(state, client.getMessages(gameId), shop);
      switch (decision.type()) {
        case SOLVE -> state = state.afterSolve(client.solve(gameId, decision.adId()));
        case BUY -> state = state.afterBuy(client.buy(gameId, decision.itemId()));
        case STOP -> {
          return new BenchGame(arm, state.score(), turn, state.lives() <= 0, "engine stopped");
        }
        default -> throw new IllegalStateException("Unhandled decision: " + decision.type());
      }
    }
    return new BenchGame(arm, state.score(), turnCap, false, "reached turn cap");
  }

  /**
   * The tier to draw this turn's pair from: one holding both an ad on its last turn and a fresh one
   * of the same label, preferring whichever such tier has been sampled least so far so the run
   * spreads across tiers instead of pooling in whatever the board favours.
   *
   * <p>Null when no tier offers both — there is nothing to randomise between, and the turn plays
   * safe instead. Ads between the two thresholds belong to neither arm: the gap is what makes the
   * contrast a contrast rather than a comparison of 1 against 2.
   */
  static ProbabilityTier pairableTier(List<Ad> board, Map<ProbabilityTier, Integer> sampled) {
    ProbabilityTier best = null;
    int fewest = Integer.MAX_VALUE;
    // values() order breaks count ties, so a board always yields the same choice.
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      if (expiringAd(board, tier) == null || freshAd(board, tier) == null) {
        continue;
      }
      int count = sampled.getOrDefault(tier, 0);
      if (count < fewest) {
        fewest = count;
        best = tier;
      }
    }
    return best;
  }

  /** The tier's ad closest to expiring, or null if it has none on its last turn. */
  static Ad expiringAd(List<Ad> board, ProbabilityTier tier) {
    return board.stream()
        .filter(a -> tierOf(a) == tier && a.expiresIn() <= EXPIRING_AT)
        .min(comparingInt(Ad::expiresIn).thenComparing(Ad::adId))
        .orElse(null);
  }

  /** The tier's ad furthest from expiring, or null if it has none with turns to spare. */
  static Ad freshAd(List<Ad> board, ProbabilityTier tier) {
    return board.stream()
        .filter(a -> tierOf(a) == tier && a.expiresIn() >= FRESH_AT)
        .min(comparingInt((Ad a) -> -a.expiresIn()).thenComparing(Ad::adId))
        .orElse(null);
  }

  /** The turn still has to be played when nothing pairs; the safest ad spends it most cheaply. */
  private static Ad safestAd(List<Ad> board) {
    // ProbabilityTier is declared safest first, so the lowest ordinal is the best odds on offer.
    return board.stream()
        .min(comparingInt((Ad a) -> tierOf(a).ordinal()).thenComparing(Ad::adId))
        .orElseThrow();
  }

  /**
   * The tier whose cheapest and dearest ad differ most, or null when no tier offers two ads at
   * different prices — without a pair there is nothing to randomise between.
   *
   * <p>Every tier is eligible, not just the mid band: the contrast is drawn *within* a tier, so each
   * one is its own stratum and a wider net simply yields more pairs. Restricting to the mid tiers
   * paired only about a third of turns, which wasted most of the budget.
   */
  private static ProbabilityTier widestSpreadTier(List<Ad> fresh) {
    ProbabilityTier best = null;
    int bestSpread = 0;
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      List<Integer> rewards = fresh.stream().filter(a -> tierOf(a) == tier).map(Ad::reward).sorted().toList();
      if (rewards.size() < 2) {
        continue;
      }
      int spread = rewards.get(rewards.size() - 1) - rewards.get(0);
      if (spread > bestSpread) {
        bestSpread = spread;
        best = tier;
      }
    }
    return best;
  }

  /**
   * Prefer this game's target tier; failing that, whichever mid tier has the fewest samples so far;
   * failing that, the safest ad on the board, purely to survive to a turn that does offer one.
   */
  private static Ad pickForLevelExperiment(
      List<Ad> fresh, ProbabilityTier target, Map<ProbabilityTier, int[]> midCounts) {
    Optional<Ad> onTarget = fresh.stream()
        .filter(a -> tierOf(a) == target)
        .max(Comparator.comparingInt(Ad::reward));
    if (onTarget.isPresent()) {
      return onTarget.get();
    }
    Optional<Ad> anyMid = fresh.stream()
        .filter(a -> MID_TIERS.contains(tierOf(a)))
        .min(Comparator.<Ad>comparingInt(a -> midSamples(midCounts, tierOf(a)))
            .thenComparing(Comparator.comparingInt(Ad::reward).reversed()));
    return anyMid.orElseGet(() -> fresh.stream()
        .min(Comparator.comparingInt(a -> tierOf(a).rank()))
        .orElseThrow());
  }

  private static int midSamples(Map<ProbabilityTier, int[]> midCounts, ProbabilityTier tier) {
    int[] cell = midCounts.get(tier);
    return cell == null ? 0 : cell[0];
  }

  /**
   * Always attempt this game's target tier when the board offers it, whatever the risk — a failed
   * attempt costs a life but still yields the data point we came for. When the target is absent,
   * attempt whichever available tier has the fewest samples so far, so a turn fills a gap in the
   * table instead of padding a tier that already has hundreds.
   */
  private static Ad pickForSampling(List<Ad> fresh, ProbabilityTier target, Map<ProbabilityTier, int[]> stats) {
    Optional<Ad> onTarget = fresh.stream()
        .filter(a -> tierOf(a) == target)
        .max(Comparator.comparingInt(Ad::reward));
    if (onTarget.isPresent()) {
      return onTarget.get();
    }
    return fresh.stream()
        .min(Comparator.<Ad>comparingInt(a -> attempts(stats, tierOf(a)))
            .thenComparing(Comparator.comparingInt(Ad::reward).reversed()))
        .orElseThrow();
  }

  /** Safe because callers only ever pass ads already filtered to a recognised tier. */
  private static ProbabilityTier tierOf(Ad ad) {
    return ProbabilityTier.fromDisplay(ad.probability()).orElseThrow();
  }

  /** How many tiers already have enough attempts to ship a measured rate. */
  private static int tiersReady(Map<ProbabilityTier, int[]> stats) {
    return (int) stats.values().stream().filter(cell -> cell[1] >= MIN_SAMPLES).count();
  }

  private static int attempts(Map<ProbabilityTier, int[]> stats, ProbabilityTier tier) {
    int[] cell = stats.get(tier);
    return cell == null ? 0 : cell[1];
  }

  private static void record(Map<ProbabilityTier, int[]> stats, ProbabilityTier tier, boolean success) {
    int[] cell = stats.computeIfAbsent(tier, t -> new int[2]);
    cell[1]++;
    if (success) {
      cell[0]++;
    }
  }

  private static ShopItem cheapestUpgrade(List<ShopItem> shop, int gold) {
    return shop.stream()
        .filter(i -> !i.id().equals("hpot") && i.cost() <= gold)
        .min(Comparator.comparingInt(ShopItem::cost))
        .orElse(null);
  }

  /**
   * The live state of one game in progress. Every experiment tracks exactly these four values
   * across a run, so they travel together rather than as four parallel locals per loop.
   */
  static final class RunState {
    int lives;
    int gold;
    int score;
    int level;

    RunState(GameState start) {
      this.lives = start.lives();
      this.gold = start.gold();
      this.score = start.score();
      this.level = start.level();
    }
  }

  /** Buys a potion and applies the new state. Healing only buys more turns to sample with. */
  private void buyPotion(String gameId, RunState state) {
    BuyResponse r = client.buy(gameId, "hpot");
    state.lives = r.lives();
    state.gold = r.gold();
    state.level = r.level();
  }

  /**
   * Attempts the cheapest affordable upgrade. Returns whether the turn was spent on it — false
   * when nothing was affordable or the shop refused, in which case the caller plays the turn
   * normally. Note a refused purchase has still cost an API call and leaves state untouched,
   * which is the original behaviour.
   */
  private boolean buyUpgrade(String gameId, List<ShopItem> shop, RunState state) {
    ShopItem upgrade = cheapestUpgrade(shop, state.gold);
    if (upgrade == null) {
      return false;
    }
    BuyResponse r = client.buy(gameId, upgrade.id());
    if (!r.shoppingSuccess()) {
      return false;
    }
    state.gold = r.gold();
    state.level = r.level();
    return true;
  }

  /** The ads worth sampling: a recognised tier, not about to expire, and worth gold. */
  private List<Ad> freshAds(String gameId, int freshThreshold) {
    List<Ad> fresh = new ArrayList<>();
    for (Ad ad : client.getMessages(gameId)) {
      if (ProbabilityTier.fromDisplay(ad.probability()).isPresent()
          && ad.expiresIn() >= freshThreshold
          && ad.reward() > 0) {
        fresh.add(ad);
      }
    }
    return fresh;
  }

  /** Applies a solve result. {@code level} is deliberately untouched — solve responses omit it. */
  private static void applySolve(RunState state, SolveResponse r) {
    state.lives = r.lives();
    state.gold = r.gold();
    state.score = r.score();
  }

  /** How a game ended: how many ads it attempted, and why it stopped. */
  record GameOutcome(int attempted, String ending) {}

  /** The ad a turn will attempt, and which end of the reward range it came from. */
  private record ArmedPick(RewardArm arm, Ad ad) {}

  /**
   * Picks this turn's ad for the reward experiment. When some tier offers a wide enough price gap,
   * flips a coin for which end of that gap to take — the same tier label sits on both, so any
   * difference between the arms is the money alone. When no tier offers a gap the turn plays safe
   * and is marked {@link RewardArm#NA}, excluding it from the contrast.
   */
  private static ArmedPick chooseRewardArm(List<Ad> fresh, Random random) {
    ProbabilityTier spreadTier = widestSpreadTier(fresh);
    if (spreadTier == null) {
      return new ArmedPick(
          RewardArm.NA, fresh.stream().min(comparingInt(a -> tierOf(a).rank())).orElseThrow());
    }
    RewardArm arm = random.nextBoolean() ? RewardArm.HIGH : RewardArm.LOW;
    List<Ad> ofTier = fresh.stream().filter(a -> tierOf(a) == spreadTier).toList();
    Ad pick = arm == RewardArm.HIGH
        ? ofTier.stream().max(comparingInt(Ad::reward)).orElseThrow()
        : ofTier.stream().min(comparingInt(Ad::reward)).orElseThrow();
    return new ArmedPick(arm, pick);
  }

  /**
   * Logs every ad that was on the board, not just the one taken. The attempt log only ever holds
   * ads the picker chose, which is a biased sample of what the game actually offered.
   */
  private static void logBoard(List<BoardRow> board, int game, int turn, int level, UpgradeArm arm,
      List<Ad> fresh, Ad pick) {
    for (Ad ad : fresh) {
      board.add(new BoardRow(game, turn, level, arm, ad.adId(), tierOf(ad), ad.reward(),
          ad.expiresIn(), ad.encrypted(), ad.adId().equals(pick.adId())));
    }
  }

  /** Picks an ad from the board, solves it, and records whatever this experiment is measuring. */
  @FunctionalInterface
  interface TurnPlay {
    SolveResponse play(int turn, List<Ad> fresh);
  }

  /**
   * Plays one game to its end. Every experiment plays the same way — heal when a life is about to
   * run out, sometimes spend the turn upgrading, otherwise attempt an ad off the fresh board — and
   * differs only in <em>when</em> it upgrades and <em>which</em> ad it takes. Those are the two
   * hooks; everything else was identical in all three run methods.
   *
   * <p>{@code state} is mutated in place, so the caller reads the final score from it.
   */
  private GameOutcome playGame(String gameId, RunState state, List<ShopItem> shop, int turnCap,
      int freshThreshold, IntPredicate upgradeTurn, TurnPlay play) {
    int attempted = 0;
    for (int turn = 0; turn < turnCap; turn++) {
      // Healing only buys more turns to sample with; it never decides which ad we attempt.
      if (state.lives <= 1 && state.gold >= 50) {
        buyPotion(gameId, state);
        continue;
      }
      if (upgradeTurn.test(turn) && buyUpgrade(gameId, shop, state)) {
        continue;
      }

      List<Ad> fresh = freshAds(gameId, freshThreshold);
      if (fresh.isEmpty()) {
        return new GameOutcome(attempted, "board ran dry");
      }

      SolveResponse r = play.play(turn, fresh);
      attempted++;
      applySolve(state, r);
      if (state.lives <= 0) {
        return new GameOutcome(attempted, "died on turn " + turn);
      }
    }
    return new GameOutcome(attempted, "reached turn cap");
  }
}
