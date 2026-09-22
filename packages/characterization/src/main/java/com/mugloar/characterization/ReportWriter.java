package com.mugloar.characterization;

import com.mugloar.characterization.Experiments.Attempt;
import com.mugloar.characterization.Experiments.Cell;
import com.mugloar.characterization.Experiments.ExpiryArm;
import com.mugloar.characterization.Experiments.BenchGame;
import com.mugloar.characterization.Experiments.BenchmarkResult;
import com.mugloar.characterization.Experiments.ExpiryResult;
import com.mugloar.characterization.Experiments.ValuationArm;
import com.mugloar.characterization.Experiments.BoardRow;
import com.mugloar.characterization.Experiments.LevelResult;
import com.mugloar.characterization.Experiments.RewardArm;
import com.mugloar.characterization.Experiments.RewardResult;
import com.mugloar.characterization.Experiments.SamplingResult;
import com.mugloar.characterization.Experiments.ShopStability;
import com.mugloar.characterization.Experiments.UpgradeArm;
import com.mugloar.domain.ProbabilityTier;
import com.mugloar.game.Probabilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/** Writes the committed artifacts: {@code probabilities.json} (the solver's data) + a report. */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ReportWriter {

  private static final int MIN_SAMPLES = Experiments.MIN_SAMPLES;

  private static final int GOAL_SCORE = com.mugloar.game.StrategyEngine.GOAL_SCORE;

  /**
   * {@code exec:java} shares the Maven JVM, so the working directory is wherever {@code mvn} was
   * launched — the repo root for {@code npm run characterize}. The pom passes the module's basedir
   * as {@code mugloar.basedir}; falling back to the working directory keeps a bare
   * {@code java -cp ...} run from the module directory working too.
   */
  private static final Path BASEDIR =
      Path.of(System.getProperty("mugloar.basedir", System.getProperty("user.dir", ".")));

  private static final Path PROBABILITIES = BASEDIR.resolve("src/main/resources/probabilities.json");
  private static final Path REPORT = BASEDIR.resolve("characterization-report.md");
  private static final Path SHOP_REPORT = BASEDIR.resolve("shop-stability-report.md");
  private static final Path LEVEL_REPORT = BASEDIR.resolve("level-experiment-report.md");
  private static final Path ATTEMPTS_CSV = BASEDIR.resolve("attempts.csv");
  private static final Path REWARD_ATTEMPTS_CSV = BASEDIR.resolve("reward-attempts.csv");
  private static final Path BOARD_CSV = BASEDIR.resolve("board.csv");
  private static final Path REWARD_REPORT = BASEDIR.resolve("reward-experiment-report.md");
  private static final Path EXPIRY_ATTEMPTS_CSV = BASEDIR.resolve("expiry-attempts.csv");
  private static final Path EXPIRY_BOARD_CSV = BASEDIR.resolve("expiry-board.csv");
  private static final Path EXPIRY_REPORT = BASEDIR.resolve("expiry-experiment-report.md");
  private static final Path BENCHMARK_CSV = BASEDIR.resolve("benchmark.csv");
  private static final Path BENCHMARK_REPORT = BASEDIR.resolve("benchmark-report.md");

  private final Probabilities priors;
  private final ObjectMapper prettyMapper;

  /**
   * One tier's outcome for this run: how many attempts it got, the probability being shipped, and
   * whether that number was measured here or carried forward. These three travelled as three
   * parallel maps keyed by tier name, read in lockstep everywhere.
   */
  private record TierSummary(ProbabilityTier tier, double used, int attempts, boolean measured) {

    String source() {
      return measured ? "measured" : "prior";
    }
  }

  void write(SamplingResult sampling) throws IOException {
    List<TierSummary> summaries = summarise(sampling);
    String probabilitiesJson = prettyMapper.writeValueAsString(envelope(summaries, sampling)) + "\n";
    writeOrDump(probabilitiesJson, buildReport(sampling, summaries));
  }

  /** A tier keeps its prior unless this run gathered enough attempts to speak for itself. */
  private List<TierSummary> summarise(SamplingResult sampling) {
    List<TierSummary> summaries = new ArrayList<>();
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      Cell cell = sampling.byTier().get(tier);
      int total = cell == null ? 0 : cell.total();
      boolean enough = total >= MIN_SAMPLES;
      double used = enough ? clamp((double) cell.success() / total) : priors.successProbability(tier);
      summaries.add(new TierSummary(tier, used, total, enough));
    }
    return summaries;
  }

  /** The shape of probabilities.json — the one artifact the running application consumes. */
  private Map<String, Object> envelope(List<TierSummary> summaries, SamplingResult sampling) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("comment", "Success probability per risk tier. Regenerated by the characterization tool "
        + "(npm run characterize). Rates measured from fresh ads only (expiresIn >= freshThreshold) to "
        + "control for the expiry confound. `measured` says whether each value came from this run's data "
        + "(>= minSamples attempts) or is a carried-forward prior — treat prior values as informed guesses, "
        + "not measurements.");
    json.put("generatedAt", LocalDate.now().toString());
    json.put("source", "live-characterization (" + sampling.games() + " games)");
    json.put("minSamples", MIN_SAMPLES);
    json.put("byTier", mapOf(summaries, TierSummary::used));
    json.put("sampleSizes", mapOf(summaries, TierSummary::attempts));
    json.put("measured", mapOf(summaries, TierSummary::measured));
    carryForwardCeilings(json);
    return json;
  }

  /**
   * Reward ceilings are measured <em>offline</em> from the pooled attempt CSVs, not by this
   * sampler, so a run must hand them back unchanged. Rebuilding this envelope from scratch and
   * forgetting them would silently delete a finding the solver depends on: without the ceiling a
   * 500-gold "Sure thing" reads as 0.98 again and walks straight through the safety floor.
   */
  void carryForwardCeilings(Map<String, Object> json) {
    Map<ProbabilityTier, Probabilities.Ceiling> ceilings = priors.rewardCeilings();
    if (ceilings.isEmpty()) {
      return;
    }
    json.put("rewardCeilingsComment", "Above these rewards a tier's label stops predicting. Measured "
        + "offline from the pooled attempt CSVs, NOT by this sampler — carried forward untouched. "
        + "Re-derive them by re-running the offline analysis, not by re-running characterize.");
    Map<String, Object> out = new LinkedHashMap<>();
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      Probabilities.Ceiling c = ceilings.get(tier);
      if (c == null) {
        continue;
      }
      Map<String, Object> cell = new LinkedHashMap<>();
      cell.put("atOrAbove", c.atOrAbove());
      cell.put("probability", c.probability());
      cell.put("samples", c.samples());
      out.put(tier.display(), cell);
    }
    json.put("rewardCeilings", out);
  }

  private static <T> Map<String, T> mapOf(
      List<TierSummary> summaries, Function<TierSummary, T> value) {
    Map<String, T> out = new LinkedHashMap<>();
    summaries.forEach(s -> out.put(s.tier().display(), value.apply(s)));
    return out;
  }

  /**
   * A run costs many minutes of live games. Never throw that away over a bad path — dump the
   * payload so it can be saved by hand instead of re-running.
   */
  private static void writeOrDump(String probabilitiesJson, String report) throws IOException {
    try {
      Files.createDirectories(PROBABILITIES.getParent());
      Files.writeString(PROBABILITIES, probabilitiesJson);
      Files.writeString(REPORT, report);
    } catch (IOException e) {
      log.error("  FAILED to write under {}: {}", BASEDIR.toAbsolutePath(), e.toString());
      log.error("  Recover by saving this as src/main/resources/probabilities.json:");
      log.error(probabilitiesJson);
      throw e;
    }
    log.info("  wrote {}", PROBABILITIES.toAbsolutePath());
    log.info("  wrote {}", REPORT.toAbsolutePath());
  }

  /**
   * Separate artifact. The shop finding is a settled one-off — prices and inventory are identical in
   * every game — so re-proving it does not need to cost 15 games of API calls on every probability
   * run. Regenerate deliberately with {@code npm run characterize:shop}.
   */
  void writeShopStability(ShopStability shop) throws IOException {
    StringBuilder md = new StringBuilder();
    md.append("# Shop Stability\n\n");
    md.append("_Generated ").append(LocalDate.now()).append("._\n\n");
    md.append("Started **").append(shop.games()).append("** fresh games and compared the shop's full ")
        .append("`(id, cost)` signature across them. Distinct layouts: **")
        .append(shop.distinctLayouts()).append("**.\n\n");
    md.append(shop.distinctLayouts() == 1
        ? "➡️ Prices and inventory are **fixed** — identical in every game, so the solver can read the "
            + "shop once per game and trust it.\n\n"
        : "➡️ Shop layout **varies** across games — the solver must re-read it.\n\n");
    md.append("| Layout signature | Games |\n| --- | ---: |\n");
    shop.layoutCounts().forEach((signature, count) ->
        md.append("| `").append(signature).append("` | ").append(count).append(" |\n"));
    md.append("\nRe-run with `npm run characterize:shop`.\n");

    writeArtifact(SHOP_REPORT, md.toString());
  }

  /**
   * Writes one artifact and announces it. Announcing per file rather than in a batch at the end
   * means a partial failure reports exactly which files did land.
   */
  private static void writeArtifact(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
    log.info("  wrote {}", path.toAbsolutePath());
  }

  /**
   * Writes the raw attempt log plus the level-experiment report. The CSV is the real artifact — the
   * report is one reading of it, and a run this expensive should outlive the question that prompted it.
   */
  void writeLevelExperiment(LevelResult result) throws IOException {
    writeArtifact(ATTEMPTS_CSV, attemptsCsv(result.attempts()));
    writeArtifact(LEVEL_REPORT, buildLevelReport(result));
  }

  /**
   * Writes the reward experiment: the paired attempt log, the full board log, and the report. The
   * board log is the new artifact — it records what was *offered*, not just what was taken, which is
   * the only way reward-against-level can be read without the picker's bias in the way.
   */
  void writeRewardExperiment(RewardResult result) throws IOException {
    writeArtifact(REWARD_ATTEMPTS_CSV, attemptsCsv(result.attempts()));
    writeArtifact(BOARD_CSV, boardCsv(result.board()));
    writeArtifact(REWARD_REPORT, buildRewardReport(result));
  }

  /**
   * Writes the expiry experiment. The board log matters as much here as in the reward run, and for
   * a new reason: it is the first unfiltered record this repo has of how an ad ages, because every
   * earlier run filtered the board to three-plus turns left before logging it.
   */
  void writeExpiryExperiment(ExpiryResult result) throws IOException {
    writeArtifact(EXPIRY_ATTEMPTS_CSV, attemptsCsv(result.attempts()));
    writeArtifact(EXPIRY_BOARD_CSV, boardCsv(result.board()));
    writeArtifact(EXPIRY_REPORT, buildExpiryReport(result));
  }

  /** Writes the solver benchmark: one row per finished game, plus the arm contrast. */
  void writeBenchmark(BenchmarkResult result) throws IOException {
    StringBuilder csv = new StringBuilder("game,arm,score,turns,died,ending\n");
    int i = 0;
    for (BenchGame g : result.games()) {
      csv.append(i++).append(',').append(g.arm()).append(',').append(g.score()).append(',')
          .append(g.turns()).append(',').append(g.died()).append(',')
          .append(csvField(g.ending())).append('\n');
    }
    writeArtifact(BENCHMARK_CSV, csv.toString());
    writeArtifact(BENCHMARK_REPORT, buildBenchmarkReport(result));
  }

  private static String buildBenchmarkReport(BenchmarkResult result) {
    StringBuilder md = new StringBuilder();
    md.append("# Solver Benchmark\n\n");
    md.append("_Generated ").append(LocalDate.now()).append(" from ").append(result.games().size())
        .append(" live games, turn cap ").append(result.turnCap()).append("._\n\n");
    md.append("Unlike the other runs here this is a **score** run: it drives ")
        .append("`StrategyEngine.decide()` and obeys it, so the numbers describe the solver rather ")
        .append("than the game. Arms alternate game by game and differ in one thing only — whether an ")
        .append("ad priced above its tier's reward ceiling is valued at the measured rate ")
        .append("(`CEILINGS`) or at its label (`LABEL_ONLY`, the previous behaviour).\n\n");

    md.append("| Arm | Games | Median | Mean | Min | Max | Reached 1000 | Died |\n");
    md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    for (ValuationArm arm : ValuationArm.values()) {
      var summary = AttemptAnalysis.summarise(
          result.games().stream().filter(g -> g.arm() == arm).toList(), GOAL_SCORE);
      md.append("| ").append(arm).append(" | ").append(summary.games()).append(" | ")
          .append(summary.median()).append(" | ").append(summary.mean()).append(" | ")
          .append(summary.min()).append(" | ").append(summary.max()).append(" | ")
          .append(String.format("%d (%.0f%%)", summary.reachedGoal(), summary.goalRate() * 100))
          .append(" | ").append(summary.died()).append(" |\n");
    }

    var ceilings = AttemptAnalysis.summarise(
        result.games().stream().filter(g -> g.arm() == ValuationArm.CEILINGS).toList(), GOAL_SCORE);
    var labels = AttemptAnalysis.summarise(
        result.games().stream().filter(g -> g.arm() == ValuationArm.LABEL_ONLY).toList(), GOAL_SCORE);
    md.append("\n**Median ").append(ceilings.median() - labels.median())
        .append(" points in favour of CEILINGS; goal rate ")
        .append(String.format("%+.0f", (ceilings.goalRate() - labels.goalRate()) * 100))
        .append("pp.**\n\n");
    md.append("Scores are heavy-tailed — a game that survives grinds on until the cap — so read the ")
        .append("median and the goal rate, not the mean. With a few dozen games per arm only a large ")
        .append("gap is worth believing; a small one is noise. Raw rows: `benchmark.csv`.\n");
    return md.toString();
  }

  /** Every ad offered, taken or not — shared by the two experiments that log the board. */
  private static String boardCsv(List<BoardRow> rows) {
    StringBuilder board = new StringBuilder("game,turn,level,arm,adId,tier,reward,expiresIn,encrypted,chosen\n");
    for (BoardRow b : rows) {
      board.append(b.game()).append(',').append(b.turn()).append(',').append(b.level()).append(',')
          .append(b.arm()).append(',').append(csvField(b.adId())).append(',')
          .append(csvField(b.tier().display())).append(',').append(b.reward()).append(',')
          .append(b.expiresIn()).append(',').append(b.encrypted() == null ? "" : b.encrypted())
          .append(',').append(b.chosen()).append('\n');
    }
    return board.toString();
  }
  private static String attemptsCsv(List<Attempt> attempts) {
    StringBuilder csv = new StringBuilder(
        "game,arm,rewardArm,expiryArm,turn,level,lives,gold,score,tier,adId,message,reward,expiresIn,success\n");
    for (Attempt a : attempts) {
      csv.append(a.game()).append(',').append(a.arm()).append(',').append(a.rewardArm()).append(',')
          .append(a.expiryArm()).append(',')
          .append(a.turn()).append(',').append(a.level()).append(',').append(a.lives()).append(',')
          .append(a.gold()).append(',').append(a.score()).append(',')
          .append(csvField(a.tier().display())).append(',').append(csvField(a.adId())).append(',')
          .append(csvField(a.message())).append(',').append(a.reward()).append(',')
          .append(a.expiresIn()).append(',').append(a.success()).append('\n');
    }
    return csv.toString();
  }

  /** Ad messages are free text and routinely contain commas and quotes. */
  private static String csvField(String value) {
    String safe = value == null ? "" : value.replace("\"", "\"\"");
    return '"' + safe + '"';
  }

  private static String buildRewardReport(RewardResult result) {
    List<Attempt> paired = result.attempts().stream()
        .filter(a -> a.rewardArm() != RewardArm.NA)
        .toList();
    StringBuilder md = new StringBuilder();
    md.append("# Reward Experiment\n\n");
    md.append("_Generated ").append(LocalDate.now()).append(" from ").append(result.games())
        .append(" live games — ").append(result.attempts().size()).append(" attempts (")
        .append(paired.size()).append(" paired), ").append(result.board().size())
        .append(" board rows. Seed ").append(result.seed()).append("._\n\n");
    md.append("**Q1. Is an ad's difficulty tied to its prize money?** Each turn the tier with the ")
        .append("widest gap between its cheapest and dearest ad is chosen, then a coin flip decides which ")
        .append("end to attempt. Both ads carry the *same label*, so a gap between the arms is the money ")
        .append("and nothing else.\n\n");
    md.append("**Q2. Do upgrades buy bigger prizes?** Games alternate upgrade arms and every ad on every ")
        .append("board is logged with the level that saw it. Rewards are already known to climb steeply ")
        .append("with the turn counter, so only level-against-level *inside a turn window* means anything.\n\n");
    md.append("Raw rows: `reward-attempts.csv` (one per solve) and `board.csv` (one per ad offered).\n\n");

    md.append("## Q1 — HIGH vs LOW reward, same tier\n\n");
    appendCrosstab(md, "Tier", AttemptAnalysis.crosstab(paired, a -> a.tier().display(), a -> a.rewardArm().name()));
    AttemptAnalysis.Rate high = AttemptAnalysis.rateOf(paired, a -> a.rewardArm() == RewardArm.HIGH);
    AttemptAnalysis.Rate low = AttemptAnalysis.rateOf(paired, a -> a.rewardArm() == RewardArm.LOW);
    md.append("\n**Pooled: HIGH ").append(high.format()).append(" vs LOW ").append(low.format())
        .append("** — difference ").append(String.format("%+.1f", (high.fraction() - low.fraction()) * 100))
        .append("pp.\n\n");
    md.append("If prize money drives difficulty, HIGH sits below LOW on every row. A flat table means the ")
        .append("label already tells the whole story and reward is just reward.\n\n");

    md.append("## Q2 — reward offered, by level, within turn window\n\n");
    md.append("Median reward of **every ad on the board**, so the picker's preferences cannot colour it.\n\n");
    appendMedianTable(md, result.board());
    md.append("\nRead down a column, not across a row: rewards climb steeply with the turn counter on their ")
        .append("own, so only a level difference *within* one turn window is evidence for upgrades.\n\n");

    md.append("## Turn budget\n\n");
    md.append("| Arm | Board rows | Attempts | Paired | Success |\n| --- | ---: | ---: | ---: | ---: |\n");
    for (UpgradeArm arm : UpgradeArm.values()) {
      List<Attempt> rows = result.attempts().stream().filter(a -> a.arm() == arm).toList();
      long boardRows = result.board().stream().filter(b -> b.arm() == arm).count();
      long pairedRows = rows.stream().filter(a -> a.rewardArm() != RewardArm.NA).count();
      md.append("| ").append(arm).append(" | ").append(boardRows).append(" | ").append(rows.size())
          .append(" | ").append(pairedRows).append(" | ")
          .append(AttemptAnalysis.rateOf(rows, a -> true).format()).append(" |\n");
    }
    md.append("\nFinal scores: ").append(result.finals()).append("\n");
    return md.toString();
  }

  /**
   * The expiry contrast. Both arms carry the same label on the same board in the same turn, so a
   * gap between them is the remaining lifetime of the ad and nothing else.
   */
  String buildExpiryReport(ExpiryResult result) {
    List<Attempt> paired = result.attempts().stream()
        .filter(a -> a.expiryArm() != ExpiryArm.NA)
        .toList();
    AttemptAnalysis.Rate expiring = AttemptAnalysis.rateOf(paired, a -> a.expiryArm() == ExpiryArm.EXPIRING);
    AttemptAnalysis.Rate fresh = AttemptAnalysis.rateOf(paired, a -> a.expiryArm() == ExpiryArm.FRESH);

    StringBuilder md = new StringBuilder();
    md.append("# Expiry Experiment\n\n");
    md.append("_Generated ").append(LocalDate.now()).append(" from ").append(result.games())
        .append(" live games — ").append(result.attempts().size()).append(" attempts (")
        .append(paired.size()).append(" paired), ").append(result.board().size())
        .append(" board rows. Seed ").append(result.seed()).append("._\n\n");
    md.append("**Does an ad about to expire succeed at the rate its label advertises?** Every number in ")
        .append("`probabilities.json` was measured on ads with ").append(Experiments.FRESH_AT)
        .append("+ turns left, so the solver applies those odds to near-expiry ads on no evidence at all. ")
        .append("Each turn this run finds a tier holding both an ad on its last turn (expiresIn ")
        .append(Experiments.EXPIRING_AT).append(") and one with ").append(Experiments.FRESH_AT)
        .append("+ left, then flips a seeded coin for which to attempt. Same label, same board, same turn.\n\n");
    md.append("Ads between the two thresholds are neither arm: the gap is what keeps this a contrast ")
        .append("rather than a comparison of one turn against two. Level is pinned at 0 — no upgrades — ")
        .append("because levelling has its own experiment.\n\n");
    md.append("Raw rows: `expiry-attempts.csv` (one per solve) and `expiry-board.csv` (one per ad ")
        .append("offered, unfiltered — the first record this repo has of ads below ")
        .append(Experiments.FRESH_AT).append(" turns left).\n\n");

    md.append("## EXPIRING vs FRESH, same tier\n\n");
    appendCrosstab(md, "Tier",
        AttemptAnalysis.crosstab(paired, a -> a.tier().display(), a -> a.expiryArm().name()));
    md.append("\n**Pooled: EXPIRING ").append(expiring.format()).append(" vs FRESH ")
        .append(fresh.format()).append("** — difference ")
        .append(String.format("%+.1f", (expiring.fraction() - fresh.fraction()) * 100)).append("pp.\n\n");
    md.append("A flat table means the label already tells the whole story and the solver is right to ")
        .append("ignore expiry. EXPIRING sitting below FRESH on most rows would mean the shipped odds are ")
        .append("optimistic for near-expiry ads, and the solver should either discount them or skip them.\n\n");

    md.append("## Against the shipped odds\n\n");
    md.append("| Tier | Shipped | EXPIRING | FRESH |\n| --- | ---: | ---: | ---: |\n");
    for (ProbabilityTier tier : ProbabilityTier.values()) {
      AttemptAnalysis.Rate tierExpiring =
          AttemptAnalysis.rateOf(paired, a -> a.tier() == tier && a.expiryArm() == ExpiryArm.EXPIRING);
      AttemptAnalysis.Rate tierFresh =
          AttemptAnalysis.rateOf(paired, a -> a.tier() == tier && a.expiryArm() == ExpiryArm.FRESH);
      if (tierExpiring.total() == 0 && tierFresh.total() == 0) {
        continue;
      }
      md.append("| ").append(tier.display()).append(" | ")
          .append(String.format("%.2f", priors.successProbability(tier))).append(" | ")
          .append(tierExpiring.format()).append(" | ").append(tierFresh.format()).append(" |\n");
    }
    md.append("\nThe shipped column is the ").append(Experiments.FRESH_AT)
        .append("+ baseline from an earlier run, so FRESH here is also a drift check on it.\n\n");

    md.append("## Confounds\n\n");
    md.append("| Arm | Median reward | Attempts |\n| --- | ---: | ---: |\n");
    for (ExpiryArm arm : List.of(ExpiryArm.EXPIRING, ExpiryArm.FRESH)) {
      List<Attempt> rows = paired.stream().filter(a -> a.expiryArm() == arm).toList();
      md.append("| ").append(arm).append(" | ").append(AttemptAnalysis.medianReward(rows))
          .append(" | ").append(rows.size()).append(" |\n");
    }
    md.append("\nReward tracks difficulty, so the arms have to be offered comparable money for the ")
        .append("contrast to be about expiry. A large gap here weakens everything above it.\n\n");

    md.append("## Pairing rate by turn window\n\n");
    md.append("| Turn window | Attempts | Paired |\n| --- | ---: | ---: |\n");
    for (String window : AttemptAnalysis.TURN_BUCKETS) {
      List<Attempt> rows = result.attempts().stream()
          .filter(a -> AttemptAnalysis.turnBucket(a.turn()).equals(window))
          .toList();
      long pairedRows = rows.stream().filter(a -> a.expiryArm() != ExpiryArm.NA).count();
      md.append("| ").append(window).append(" | ").append(rows.size()).append(" | ")
          .append(pairedRows).append(" |\n");
    }
    md.append("\nAn ad needs ").append(7 - Experiments.EXPIRING_AT)
        .append(" unsolved turns to reach its last one, so early turns cannot pair. A pairing rate ")
        .append("that stays low in the later windows means the run bought less evidence than its ")
        .append("game count suggests.\n\n");

    md.append("Final scores: ").append(result.finals()).append("\n");
    return md.toString();
  }

  /** Median offered reward per (turn window, level bucket) — the Q2 table. */
  private static void appendMedianTable(StringBuilder md, List<BoardRow> board) {
    List<String> levels = AttemptAnalysis.LEVEL_BUCKETS;
    appendHeader(md, "Turn window", levels.stream().map(l -> "L" + l).toList());
    AttemptAnalysis.medianRewardByBucket(board).forEach((window, cells) -> {
      md.append("| ").append(window);
      levels.forEach(level -> md.append(" | ").append(cells.get(level)));
      md.append(" |\n");
    });
  }

  /** A markdown header row plus its right-aligned separator. */
  private static void appendHeader(StringBuilder md, String corner, List<String> columns) {
    md.append("| ").append(corner);
    columns.forEach(c -> md.append(" | ").append(c));
    md.append(" |\n| ---");
    columns.forEach(c -> md.append(" | ---:"));
    md.append(" |\n");
  }

  private static String buildLevelReport(LevelResult result) {
    List<Attempt> all = result.attempts();
    StringBuilder md = new StringBuilder();
    md.append("# Level Experiment\n\n");
    md.append("_Generated ").append(LocalDate.now()).append(" from ").append(result.games())
        .append(" live games, ").append(all.size()).append(" logged attempts._\n\n");
    md.append("**Question.** Does the dragon's level change its odds, or does the game scale difficulty ")
        .append("so the two cancel out?\n\n");
    md.append("**Design.** Every game is assigned an upgrade arm — `NONE` never buys an upgrade, `FAST` ")
        .append("buys the cheapest affordable one at every chance — round-robin against a target tier drawn ")
        .append("from the mid band (")
        .append(String.join(", ", Experiments.MID_TIERS.stream().map(ProbabilityTier::display).toList()))
        .append("). Because the arm is *assigned* rather than earned, level is no longer glued to the turn ")
        .append("counter, and the two can be told apart. Potions are allowed in both arms: they keep games ")
        .append("alive for comparable lengths without touching level.\n\n");
    md.append("Raw rows are in `attempts.csv` — re-analyse from there rather than re-running.\n\n");

    md.append("## 1. Does levelling help? (`FAST` vs `NONE`, the causal contrast)\n\n");
    appendCrosstab(md, "Tier", AttemptAnalysis.crosstab(all, a -> a.tier().display(), a -> a.arm().name()));
    md.append("\nA real level effect shows up as `FAST` beating `NONE` on the same row. Compare like for ")
        .append("like — see table 2 before drawing a conclusion, since the arms do not spend their turns ")
        .append("identically.\n\n");

    md.append("## 2. Does the game scale with time? (arm `NONE` only, where level never moves)\n\n");
    List<Attempt> none = all.stream().filter(a -> a.arm() == UpgradeArm.NONE).toList();
    appendCrosstab(md, "Tier", AttemptAnalysis.crosstab(none, a -> a.tier().display(),
        a -> AttemptAnalysis.turnBucket(a.turn())));
    md.append("\nLevel is fixed down every row here, so a decline left-to-right is difficulty scaling with ")
        .append("the turn counter — and would mean late-game ads are harder than their label implies.\n\n");

    md.append("## 3. Success by level reached (observational — read with care)\n\n");
    appendCrosstab(md, "Tier", AttemptAnalysis.crosstab(all, a -> a.tier().display(),
        a -> "L" + AttemptAnalysis.levelBucket(a.level())));
    md.append("\n⚠️ This table pools both arms, so level still correlates with turn number: a high-level ")
        .append("attempt is also a late attempt. It cannot separate the two on its own — it is here because ")
        .append("it is the table everyone expects to see, and tables 1 and 2 are what actually answer the ")
        .append("question.\n\n");

    md.append("## Turn budget spent\n\n");
    md.append("| Arm | Attempts | Games | Overall success |\n| --- | ---: | ---: | ---: |\n");
    for (UpgradeArm arm : UpgradeArm.values()) {
      List<Attempt> armRows = all.stream().filter(a -> a.arm() == arm).toList();
      long games = armRows.stream().mapToInt(Attempt::game).distinct().count();
      md.append("| ").append(arm).append(" | ").append(armRows.size()).append(" | ").append(games)
          .append(" | ").append(AttemptAnalysis.rateOf(armRows, a -> true).format()).append(" |\n");
    }
    md.append("\nFinal scores: ").append(result.finals()).append("\n");
    return md.toString();
  }

  private static void appendCrosstab(
      StringBuilder md, String corner, Map<String, Map<String, AttemptAnalysis.Rate>> table) {
    List<String> columns = AttemptAnalysis.columnsOf(table);
    md.append("| ").append(corner);
    columns.forEach(c -> md.append(" | ").append(c));
    md.append(" |\n| ---");
    columns.forEach(c -> md.append(" | ---:"));
    md.append(" |\n");
    table.forEach((row, cells) -> {
      md.append("| ").append(row);
      columns.forEach(c -> md.append(" | ").append(cells.getOrDefault(c, new AttemptAnalysis.Rate(0, 0)).format()));
      md.append(" |\n");
    });
  }

  private static String buildReport(SamplingResult sampling, List<TierSummary> summaries) {
    double avg = sampling.finals().stream().mapToInt(Integer::intValue).average().orElse(0);
    int attempts = summaries.stream().mapToInt(TierSummary::attempts).sum();
    long covered = summaries.stream().filter(TierSummary::measured).count();
    StringBuilder md = new StringBuilder();
    md.append("# Characterization Report\n\n");
    md.append("_Generated ").append(LocalDate.now()).append(" from ").append(sampling.games()).append(" live games._\n\n");
    md.append("Measures the parts of the game the API does not document. The solver consumes the resulting "
        + "`probabilities.json`, so every constant it uses is traceable to a measurement here.\n\n");
    md.append("Shop stability is measured separately and does not change — see `shop-stability-report.md` "
        + "(`npm run characterize:shop`).\n\n");

    md.append("## Sampling run outcome\n\n");
    md.append("⚠️ These scores are **not** a measure of solver quality. This run is a measurement run: it "
        + "attempts deadly tiers on purpose, at one life if need be, because a lost game still yields the "
        + "attempts we came for. Expect low scores here — the solver's real win rate is measured separately.\n\n");
    md.append("Total logged attempts: **").append(attempts).append("** across ").append(sampling.games())
        .append(" games. Tiers with enough data: **").append(covered).append("/")
        .append(ProbabilityTier.values().length).append("**.\n");
    md.append("Games that still passed 1000: ").append(sampling.wins()).append("/").append(sampling.games())
        .append(". Final scores: ").append(sampling.finals()).append(". Average: ").append(Math.round(avg)).append(".\n\n");

    md.append("## P(success | tier)\n\n");
    md.append("Measured from **fresh ads only** (expiresIn ≥ freshThreshold). A row marked `prior` had fewer "
        + "than ").append(MIN_SAMPLES).append(" attempts, so it kept its previous value — an informed guess, "
        + "not a measurement. Raise `CHAR_GAMES` to convert those rows to `measured`.\n\n");
    md.append("| Tier | Attempts | Used (shipped) | Source |\n| --- | ---: | ---: | --- |\n");
    for (TierSummary s : summaries) {
      md.append("| ").append(s.tier().display()).append(" | ").append(s.attempts())
          .append(" | ").append(Math.round(s.used() * 100)).append("% | ")
          .append(s.source()).append(" |\n");
    }
    md.append("\n## Notes\n\n");
    md.append("- `solve` responses carry no `level` field — only `buy` does — so level is tracked from purchases.\n");
    md.append("- Encrypted ads: `encrypted:1` is Base64, `encrypted:2` is ROT13 (validated against live samples).\n");
    md.append("- Each game targets one tier (`gameIndex % 11`), so the run needs to be a comfortable multiple "
        + "of 11 games before every tier is hunted evenly.\n");
    md.append("- Larger samples: `CHAR_GAMES=80 npm run characterize`.\n");
    return md.toString();
  }

  private static double clamp(double p) {
    return Math.round(Math.min(0.99, Math.max(0.02, p)) * 100) / 100.0;
  }
}
