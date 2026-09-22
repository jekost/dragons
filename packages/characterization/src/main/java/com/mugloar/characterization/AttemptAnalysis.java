package com.mugloar.characterization;

import com.mugloar.characterization.Experiments.Attempt;
import com.mugloar.characterization.Experiments.BenchGame;
import com.mugloar.characterization.Experiments.BoardRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Turns a list of logged {@link Attempt}s into the tables the experiment reports print.
 *
 * <p>Deliberately pure and free of any API contact so it can be unit-tested against synthetic
 * attempts: the live runs cost the better part of an hour, and the arithmetic that interprets it is
 * the part most likely to be quietly wrong.
 */
final class AttemptAnalysis {

  private AttemptAnalysis() {}

  /**
   * The complete vocabulary each bucketer can return, in report order. Reports iterate these
   * rather than re-declaring the labels, so a changed boundary cannot silently desync the two
   * sides into an all-dashes table.
   */
  static final List<String> TURN_BUCKETS = List.of("00-09", "10-19", "20+");

  static final List<String> LEVEL_BUCKETS = List.of("0", "1-2", "3-4", "5+");

  /** Bucket labels are chosen to sort correctly as plain strings, so a TreeMap orders them. */
  static String turnBucket(int turn) {
    if (turn < 10) {
      return "00-09";
    }
    return turn < 20 ? "10-19" : "20+";
  }

  static String levelBucket(int level) {
    if (level <= 0) {
      return "0";
    }
    if (level <= 2) {
      return "1-2";
    }
    return level <= 4 ? "3-4" : "5+";
  }

  /** Successes out of attempts. {@code total == 0} renders as a dash rather than a fake 0%. */
  record Rate(int success, int total) {

    Rate plus(boolean hit) {
      return new Rate(success + (hit ? 1 : 0), total + 1);
    }

    double fraction() {
      return total == 0 ? 0.0 : (double) success / total;
    }

    int percent() {
      return (int) Math.round(fraction() * 100);
    }

    /** {@code "61% (57/94)"}, or {@code "—"} when nothing was sampled. */
    String format() {
      return total == 0 ? "—" : percent() + "% (" + success + "/" + total + ")";
    }
  }

  static Rate rateOf(List<Attempt> attempts, Predicate<Attempt> filter) {
    Rate rate = new Rate(0, 0);
    for (Attempt attempt : attempts) {
      if (filter.test(attempt)) {
        rate = rate.plus(attempt.success());
      }
    }
    return rate;
  }

  /**
   * Cross-tabulates attempts: outer key is the row, inner key the column. Every row carries the
   * same column set (missing combinations become empty {@link Rate}s) so the markdown table is
   * rectangular and obvious gaps stay visible instead of silently collapsing.
   */
  static Map<String, Map<String, Rate>> crosstab(
      List<Attempt> attempts, Function<Attempt, String> row, Function<Attempt, String> column) {
    Map<String, Map<String, Rate>> table = new TreeMap<>();
    Map<String, Boolean> columns = new TreeMap<>();
    for (Attempt attempt : attempts) {
      columns.put(column.apply(attempt), Boolean.TRUE);
    }
    for (Attempt attempt : attempts) {
      Map<String, Rate> cells = table.computeIfAbsent(row.apply(attempt), r -> {
        Map<String, Rate> blank = new LinkedHashMap<>();
        columns.keySet().forEach(c -> blank.put(c, new Rate(0, 0)));
        return blank;
      });
      cells.merge(column.apply(attempt), new Rate(attempt.success() ? 1 : 0, 1),
          (a, b) -> new Rate(a.success() + b.success(), a.total() + b.total()));
    }
    return table;
  }

  /**
   * Median offered reward per (turn window, level bucket), with the sample size behind each. Every
   * combination gets a cell — an unsampled one is a dash, so a gap stays visible rather than
   * reading as a median of zero.
   *
   * <p>One grouping pass rather than re-scanning the board per cell.
   */
  static Map<String, Map<String, String>> medianRewardByBucket(List<BoardRow> board) {
    Map<String, Map<String, List<Integer>>> grouped = new TreeMap<>();
    for (BoardRow row : board) {
      grouped
          .computeIfAbsent(turnBucket(row.turn()), w -> new TreeMap<>())
          .computeIfAbsent(levelBucket(row.level()), l -> new ArrayList<>())
          .add(row.reward());
    }

    Map<String, Map<String, String>> table = new LinkedHashMap<>();
    for (String window : TURN_BUCKETS) {
      Map<String, String> cells = new LinkedHashMap<>();
      for (String level : LEVEL_BUCKETS) {
        cells.put(level, formatMedian(grouped.getOrDefault(window, Map.of()).get(level)));
      }
      table.put(window, cells);
    }
    return table;
  }

  /**
   * Median reward of a set of attempts, for checking that two arms were offered comparable money.
   * Reward tracks difficulty, so an arm that happens to hold dearer ads would look worse for a
   * reason that has nothing to do with the thing under test.
   */
  static String medianReward(List<Attempt> attempts) {
    return formatMedian(attempts.stream().map(Attempt::reward).toList());
  }

  /** {@code "93 (n=41)"}, or a dash when that combination was never offered. */
  private static String formatMedian(List<Integer> rewards) {
    if (rewards == null || rewards.isEmpty()) {
      return "—";
    }
    List<Integer> sorted = rewards.stream().sorted().toList();
    return sorted.get(sorted.size() / 2) + " (n=" + sorted.size() + ")";
  }

  /**
   * One arm's score distribution. Median rather than mean is the headline: a solver that dies early
   * in a few games and grinds forever in others has a mean that describes neither.
   */
  record ScoreSummary(int games, int median, int mean, int min, int max, int reachedGoal, int died) {

    double goalRate() {
      return games == 0 ? 0.0 : (double) reachedGoal / games;
    }
  }

  static ScoreSummary summarise(List<BenchGame> played, int goalScore) {
    if (played.isEmpty()) {
      return new ScoreSummary(0, 0, 0, 0, 0, 0, 0);
    }
    List<Integer> scores = played.stream().map(BenchGame::score).sorted().toList();
    int total = scores.stream().mapToInt(Integer::intValue).sum();
    return new ScoreSummary(
        scores.size(),
        scores.get(scores.size() / 2),
        total / scores.size(),
        scores.get(0),
        scores.get(scores.size() - 1),
        (int) played.stream().filter(g -> g.score() >= goalScore).count(),
        (int) played.stream().filter(BenchGame::died).count());
  }

  /** Ordered column labels for a crosstab, so headers and cells line up. */
  static List<String> columnsOf(Map<String, Map<String, Rate>> table) {
    return table.values().stream()
        .findFirst()
        .map(row -> List.copyOf(row.keySet()))
        .orElseGet(List::of);
  }
}
