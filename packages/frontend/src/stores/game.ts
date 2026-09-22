import {
  GOAL_SCORE,
  type GameSnapshot,
  type InvestigationResult,
  type Reputation,
  type StepResult,
} from '../types';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

export { GOAL_SCORE };

export type GameStatus = 'idle' | 'ready' | 'autoplaying' | 'paused' | 'over';
export type LogKind = 'start' | 'solve' | 'fail' | 'buy' | 'stop' | 'investigate' | 'error';

/**
 * A reputation lookup, stamped with the turn it was read at. Reputation moves as the game is
 * played and refreshing it costs another turn, so a reading is only ever a reading — the HUD says
 * when it was taken rather than implying it is live.
 */
export interface ReputationReading extends Reputation {
  turn: number;
}

export interface LogEntry {
  id: number;
  turn: number;
  kind: LogKind;
  text: string;
}

let logId = 0;
const nextLogId = () => (logId += 1);

/**
 * Bounds for the auto-play delay slider. One family, in one place: the slider's min/max/step and
 * the starting value used to be four numbers spread across this file and Controls.vue, with
 * nothing keeping them consistent.
 */
export const AUTO_DELAY = { min: 100, max: 1500, step: 100, default: 500 } as const;

type LogLine = { kind: LogKind; text: string };

function buyLine(reason: string, buy: NonNullable<StepResult['buy']>): LogLine {
  return {
    kind: 'buy',
    text: `${reason} → lives ${buy.lives}, gold ${buy.gold}, level ${buy.level}.`,
  };
}

function solveLine(
  reason: string,
  questMessage: string,
  solve: NonNullable<StepResult['solve']>,
): LogLine {
  return {
    kind: solve.success ? 'solve' : 'fail',
    text: solve.success
      ? `✅ ${reason} → +score, now ${solve.score} (gold ${solve.gold}).`
      : `❌ Failed: "${questMessage}" — lost a life (lives ${solve.lives}).`,
  };
}

/**
 * Turns a step result into a log line. The trailing return covers both an explicit `stop` and the
 * case where a decision arrives without the matching result payload — the reason is the useful
 * thing to show either way.
 */
function toLogLine(step: StepResult): LogLine {
  const { decision } = step;
  if (decision.type === 'buy' && step.buy) {
    return buyLine(decision.reason, step.buy);
  }
  if (decision.type === 'solve' && step.solve) {
    return solveLine(decision.reason, decision.ad.message, step.solve);
  }
  return { kind: 'stop', text: decision.reason };
}

/**
 * Central game store (Pinia). Holds durable game state and the decision log; server-call
 * state (in-flight/errors) is handled separately by TanStack Query in the composables.
 *
 * Note: reaching {@link GOAL_SCORE} (1000) sets `goalReached` but does NOT end the game — the
 * bot plays on to maximize score. The only terminal state is `over` (lives run out).
 */
export const useGameStore = defineStore('game', () => {
  const snapshot = ref<GameSnapshot | null>(null);
  const log = ref<LogEntry[]>([]);
  const status = ref<GameStatus>('idle');
  const goalReached = ref(false);
  const error = ref<string | null>(null);
  const reputation = ref<ReputationReading | null>(null);
  const autoDelayMs = ref<number>(AUTO_DELAY.default);

  const gameId = computed(() => snapshot.value?.state.gameId ?? null);

  /**
   * The three questions the UI keeps asking. They lived as loose `status === '...'` comparisons
   * in four files, which is how `App` and `Controls` ended up with two differently shaped
   * definitions of the same "can the user act right now".
   */
  const hasGame = computed(() => status.value !== 'idle');
  const isOver = computed(() => status.value === 'over');
  const isAutoplaying = computed(() => status.value === 'autoplaying');

  /** A game exists and is still alive. Being busy on the network is a separate question. */
  const canPlay = computed(() => hasGame.value && !isOver.value);

  const recommendation = computed(() => snapshot.value?.recommendation ?? null);
  const recommendedAdId = computed(() =>
    recommendation.value?.type === 'solve' ? recommendation.value.adId : null,
  );
  const recommendedItemId = computed(() =>
    recommendation.value?.type === 'buy' ? recommendation.value.itemId : null,
  );

  /**
   * Builds a log entry stamped with the current turn. Call it *after* `snapshot` has been updated
   * so the entry carries the turn it describes; with no game in progress the turn reads 0.
   */
  function logEntry(kind: LogKind, text: string): LogEntry {
    return { id: nextLogId(), turn: snapshot.value?.state.turn ?? 0, kind, text };
  }

  function startFromSnapshot(snap: GameSnapshot): void {
    snapshot.value = snap;
    status.value = 'ready';
    goalReached.value = snap.state.score >= GOAL_SCORE;
    error.value = null;
    log.value = [logEntry('start', `Game ${snap.state.gameId} started.`)];
  }

  function applyStep(step: StepResult): void {
    const { kind, text } = toLogLine(step);
    const state = step.snapshot.state;
    const gameOver = (step.solve && step.solve.lives <= 0) || state.lives <= 0;
    snapshot.value = step.snapshot;
    if (state.score >= GOAL_SCORE) goalReached.value = true;
    if (gameOver) status.value = 'over'; // 1000 is a milestone, not a stop — only death ends it
    log.value.push(logEntry(kind, text));
  }

  /**
   * Applies a reputation lookup. The snapshot always lands — investigating spends a turn, so the
   * board that comes back is a turn older than the one on screen and has to replace it either way.
   */
  function applyInvestigation(result: InvestigationResult): void {
    snapshot.value = result.snapshot;
    if (!result.reputation) {
      log.value.push(logEntry('stop', 'Game over — nothing left to investigate.'));
      return;
    }
    const { people, state, underworld } = result.reputation;
    reputation.value = { people, state, underworld, turn: result.snapshot.state.turn };
    log.value.push(
      logEntry(
        'investigate',
        `🔍 Reputation — people ${people.toFixed(2)}, state ${state.toFixed(2)}, ` +
          `underworld ${underworld.toFixed(2)}. Cost 1 turn.`,
      ),
    );
  }

  function setStatus(next: GameStatus): void {
    status.value = next;
  }

  function setError(next: string | null): void {
    error.value = next;
    if (next) {
      log.value.push(logEntry('error', next));
    }
  }

  function setAutoDelay(ms: number): void {
    autoDelayMs.value = ms;
  }

  /** Clears the game. `autoDelayMs` deliberately survives — playback speed is a user preference. */
  function reset(): void {
    snapshot.value = null;
    log.value = [];
    status.value = 'idle';
    goalReached.value = false;
    error.value = null;
    reputation.value = null;
  }

  return {
    snapshot,
    log,
    status,
    goalReached,
    error,
    reputation,
    autoDelayMs,
    gameId,
    hasGame,
    isOver,
    isAutoplaying,
    canPlay,
    recommendation,
    recommendedAdId,
    recommendedItemId,
    startFromSnapshot,
    applyStep,
    applyInvestigation,
    setStatus,
    setError,
    setAutoDelay,
    reset,
  };
});
