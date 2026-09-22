package com.mugloar.characterization;

import com.mugloar.client.GameApiClient;
import com.mugloar.config.GameProperties;
import com.mugloar.domain.ProbabilityTier;
import com.mugloar.game.Probabilities;
import java.util.EnumMap;
import com.mugloar.game.StrategyEngine;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Offline tool that measures the game's hidden mechanics against the live API. Run it with
 * {@code npm run characterize[:shop|:level|:reward]}; never as part of the application.
 *
 * <p>Four modes, selected by the first argument:
 * <ul>
 *   <li><b>(default)</b> — P(success | tier). The only measurement the solver reads, via the
 *       {@code probabilities.json} this writes.</li>
 *   <li>{@code shop} — re-proves that prices and inventory never vary between games. A settled
 *       one-off, which is why it no longer costs 15 games on every probability run.</li>
 *   <li>{@code level} — does levelling change the odds, or does the game scale to match?</li>
 *   <li>{@code reward} — is difficulty tied to prize money, and do upgrades buy bigger prizes?</li>

 *   <li>{@code expiry} — does an ad about to expire succeed at the rate its label advertises?</li>
 *   <li>{@code benchmark} — the only <em>score</em> run: plays whole games through the real
 *       solver, A/B-ing the reward ceilings against the label-only valuation.</li>
 * </ul>
 *
 * <p>Tunable via env vars: {@code CHAR_GAMES}, {@code CHAR_TURN_CAP}, {@code CHAR_SHOP_GAMES},
 * {@code CHAR_LEVEL_GAMES}, {@code CHAR_REWARD_GAMES}, {@code CHAR_EXPIRY_GAMES},
 * {@code CHAR_BENCH_GAMES}, {@code CHAR_BENCH_TURN_CAP}, {@code CHAR_SEED},
 * {@code GAME_API_BASE_URL}.
 */
@Slf4j
public final class CharacterizationMain {

  private CharacterizationMain() {}

  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final int MAX_RETRIES = 4;

  public static void main(String[] args) throws Exception {
    String baseUrl = env("GAME_API_BASE_URL", "https://dragonsofmugloar.com/api/v2");
    ObjectMapper plain = JsonMapper.builder().build();
    ObjectMapper pretty = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    // Longer timeout and one more retry than the app: a measurement run is minutes of live calls
    // and would rather wait than lose the games it has already spent.
    GameApiClient client =
        new GameApiClient(
            new GameProperties.Api(baseUrl, TIMEOUT.toMillis(), MAX_RETRIES), RestClient.builder(), plain);
    Experiments experiments = new Experiments(client);
    ReportWriter writer = new ReportWriter(new Probabilities(plain), pretty);

    String mode = args.length > 0 ? args[0].toLowerCase() : "probabilities";
    switch (mode) {
      case "shop" -> runShopStability(experiments, writer);
      case "level" -> runLevelExperiment(experiments, writer);
      case "reward" -> runRewardExperiment(experiments, writer);
      case "expiry" -> runExpiryExperiment(experiments, writer);
      case "benchmark" -> runSolverBenchmark(experiments, writer, plain);
      default -> runProbabilities(experiments, writer);
    }
  }

  /** The default run: the measurement the solver actually consumes. */
  private static void runProbabilities(Experiments experiments, ReportWriter writer) throws Exception {
    int probGames = intEnv("CHAR_GAMES", 40);
    int turnCap = intEnv("CHAR_TURN_CAP", 50);

    log.info("Characterizing via live API (probability games={}, turn cap={})...", probGames, turnCap);

    log.info("[1/2] Probability sampling (many calls, many lost games; be patient)...");
    var sampling = experiments.runProbabilitySampling(probGames, turnCap, 3);
    printTierTally(sampling);

    log.info("[2/2] Writing artifacts...");
    writer.write(sampling);
    log.info("Done.");
  }

  /** Opt-in run: re-proves that shop prices and inventory never vary between games. */
  private static void runShopStability(Experiments experiments, ReportWriter writer) throws Exception {
    int shopGames = intEnv("CHAR_SHOP_GAMES", 15);

    log.info("Checking shop stability via live API ({} games)...", shopGames);

    log.info("[1/2] Starting games and comparing shop layouts...");
    var shop = experiments.runShopStability(shopGames);
    log.info("  distinct layouts: {}", shop.distinctLayouts());

    log.info("[2/2] Writing artifact...");
    writer.writeShopStability(shop);
    log.info("Done.");
  }

  /**
   * Opt-in run: does levelling change the odds, or does the game scale to match? Sized to the same
   * budget as the default probability run (same games, same turn cap), but spent entirely on the mid
   * tiers, where success sits near 0.5 and a shift is actually detectable.
   */
  private static void runLevelExperiment(Experiments experiments, ReportWriter writer) throws Exception {
    int games = intEnv("CHAR_LEVEL_GAMES", 40);
    int turnCap = intEnv("CHAR_TURN_CAP", 50);

    log.info("Level experiment via live API (games={}, turn cap={}, mid tiers={})...",
        games, turnCap, Experiments.MID_TIERS.size());

    log.info("[1/2] Playing assigned arms (NONE never upgrades, FAST always does)...");
    var result = experiments.runLevelExperiment(games, turnCap, 3);
    log.info("");
    log.info("  logged {} attempts", result.attempts().size());

    log.info("[2/2] Writing artifacts...");
    writer.writeLevelExperiment(result);
    log.info("Done.");
  }

  /**
   * Opt-in run: is difficulty tied to prize money, and do upgrades buy bigger prizes? Same budget as
   * the other runs. The coin flip that picks HIGH or LOW is seeded so a run can be reproduced —
   * CHAR_SEED overrides it, and the seed used is recorded in the report.
   */
  private static void runRewardExperiment(Experiments experiments, ReportWriter writer) throws Exception {
    int games = intEnv("CHAR_REWARD_GAMES", 40);
    int turnCap = intEnv("CHAR_TURN_CAP", 50);
    long seed = intEnv("CHAR_SEED", 20260918);

    log.info("Reward experiment via live API (games={}, turn cap={}, seed={})...",
        games, turnCap, seed);

    log.info("[1/2] Playing; each turn flips HIGH vs LOW reward within one tier...");
    var result = experiments.runRewardExperiment(games, turnCap, 3, seed);
    long pairedCount = result.attempts().stream()
        .filter(a -> a.rewardArm() != Experiments.RewardArm.NA)
        .count();
    log.info("");
    log.info("  logged {} attempts ({} paired) and {} board rows",
        result.attempts().size(), pairedCount, result.board().size());

    log.info("[2/2] Writing artifacts...");
    writer.writeRewardExperiment(result);
    log.info("Done.");
  }

  /**
   * Opt-in run: does an ad on its last turn succeed at the rate its label advertises? The shipped
   * odds were measured on ads with turns to spare, so near-expiry ads are extrapolation. The coin
   * flip that picks EXPIRING or FRESH is seeded, and the seed is recorded in the report.
   */
  private static void runExpiryExperiment(Experiments experiments, ReportWriter writer) throws Exception {
    int games = intEnv("CHAR_EXPIRY_GAMES", 40);
    int turnCap = intEnv("CHAR_TURN_CAP", 50);
    long seed = intEnv("CHAR_SEED", 20260918);

    log.info("Expiry experiment via live API (games={}, turn cap={}, seed={})...",
        games, turnCap, seed);

    log.info("[1/2] Playing; each turn flips EXPIRING vs FRESH within one tier...");
    var result = experiments.runExpiryExperiment(games, turnCap, seed);
    long pairedCount = result.attempts().stream()
        .filter(a -> a.expiryArm() != Experiments.ExpiryArm.NA)
        .count();
    log.info("");
    log.info("  logged {} attempts ({} paired) and {} board rows",
        result.attempts().size(), pairedCount, result.board().size());

    log.info("[2/2] Writing artifacts...");
    writer.writeExpiryExperiment(result);
    log.info("Done.");
  }

  /**
   * The only run that measures the solver rather than the game. Both arms share one
   * {@code probabilities.json}; the control simply ignores the reward ceilings in it, so nothing
   * but the behaviour under test can differ. Games are long — the solver plays until it dies — so
   * the cap is far higher than the measurement runs use.
   */
  private static void runSolverBenchmark(
      Experiments experiments, ReportWriter writer, ObjectMapper plain) throws Exception {
    int gamesPerArm = intEnv("CHAR_BENCH_GAMES", 30);
    int turnCap = intEnv("CHAR_BENCH_TURN_CAP", 200);

    var withCeilings = new StrategyEngine(new Probabilities(plain));
    var labelOnly = new StrategyEngine(new LabelOnlyProbabilities(plain));
    var engines = new EnumMap<Experiments.ValuationArm, StrategyEngine>(Experiments.ValuationArm.class);
    engines.put(Experiments.ValuationArm.CEILINGS, withCeilings);
    engines.put(Experiments.ValuationArm.LABEL_ONLY, labelOnly);

    log.info("Solver benchmark via live API ({} games per arm, turn cap {})...",
        gamesPerArm, turnCap);
    log.info("[1/2] Playing whole games through StrategyEngine.decide()...");
    var result = experiments.runSolverBenchmark(gamesPerArm, turnCap, engines);

    log.info("");
    for (Experiments.ValuationArm arm : Experiments.ValuationArm.values()) {
      var s = AttemptAnalysis.summarise(
          result.games().stream().filter(g -> g.arm() == arm).toList(), StrategyEngine.GOAL_SCORE);
      log.info("  %-10s median %6d   mean %6d   reached 1000: %d/%d   died %d".formatted(
          arm, s.median(), s.mean(), s.reachedGoal(), s.games(), s.died()));
    }

    log.info("[2/2] Writing artifacts...");
    writer.writeBenchmark(result);
    log.info("Done.");
  }

  /** The whole point of the run, in the console, so you do not have to open a file to see it. */
  private static void printTierTally(Experiments.SamplingResult sampling) {
    log.info("");
    log.info("  P(success | tier) from this run:");
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      Experiments.Cell cell = sampling.byTier().get(tier);
      int total = cell == null ? 0 : cell.total();
      String verdict = total >= Experiments.MIN_SAMPLES
          ? String.format("%3d%%  measured", Math.round(100.0 * cell.success() / total))
          : "      prior kept (too few attempts)";
      log.info("    %-20s %4d attempts   %s".formatted(tier.display(), total, verdict));
    }
    log.info("");
    log.info("  NOTE: scores below are low on purpose - this run suicides to sample deadly tiers.");
    log.info("  games past 1000: {}/{}  finals: {}",
        sampling.wins(), sampling.games(), sampling.finals());
    log.info("");
  }

  private static String env(String key, String fallback) {
    String v = System.getenv(key);
    return v == null ? fallback : v;
  }

  private static int intEnv(String key, int fallback) {
    try {
      return Integer.parseInt(env(key, String.valueOf(fallback)));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
