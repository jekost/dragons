<script setup lang="ts">
import type { GameState } from '../types';
import { computed } from 'vue';
import type { GameStatus, ReputationReading } from '../stores/game.js';
import ScoreProgress from './ScoreProgress.vue';
import StatTile from './StatTile.vue';
import StatusBadges from './StatusBadges.vue';

const props = withDefaults(
  defineProps<{
    state: GameState | null;
    status: GameStatus;
    goalReached?: boolean;
    /** Null until the user spends a turn looking it up — there is no free way to know it. */
    reputation?: ReputationReading | null;
  }>(),
  { goalReached: false, reputation: null },
);

/** Past this many lives the hearts become unreadable, so the count is shown instead. */
const MAX_HEARTS = 8;
/** Hearts per line: the Lives tile is narrow, so a fifth heart wraps to its own row. */
const HEARTS_PER_ROW = 4;

/** One object to read the figures off, so every tile stops repeating `?? 0`. */
const EMPTY: Pick<GameState, 'score' | 'lives' | 'gold' | 'level' | 'turn' | 'highScore'> = {
  score: 0,
  lives: 0,
  gold: 0,
  level: 0,
  turn: 0,
  highScore: 0,
};

const stats = computed(() => props.state ?? EMPTY);

/**
 * Reputation runs negative as readily as positive, so the sign is the point — and it moves in
 * tenths, so two decimals are the difference between a figure that changes and one that reads 0
 * all game.
 */
function signed(value: number): string {
  const shown = value.toFixed(2);
  return value > 0 ? `+${shown}` : shown.replace('-', '−');
}

const factions = computed(() =>
  props.reputation
    ? ([
        ['People', props.reputation.people],
        ['State', props.reputation.state],
        ['Underworld', props.reputation.underworld],
      ] as const)
    : [],
);

/** A reading taken before the current turn is history, not status. */
const stale = computed(() => !!props.reputation && stats.value.turn > props.reputation.turn);
const tooManyHearts = computed(() => stats.value.lives > MAX_HEARTS);
/** The hearts to draw, split into rows of HEARTS_PER_ROW; empty when the dragon is dead. */
const heartRows = computed(() => {
  const shown = Math.min(stats.value.lives, MAX_HEARTS);
  const rows: string[] = [];
  for (let i = 0; i < shown; i += HEARTS_PER_ROW) {
    rows.push('♥︎'.repeat(Math.min(HEARTS_PER_ROW, shown - i)));
  }
  return rows;
});
</script>

<template>
  <section class="hud" aria-label="Game state">
    <StatusBadges :status="status" :goal-reached="goalReached" :game-id="state?.gameId ?? null" />

    <div class="stats">
      <StatTile label="Lives">
        <span class="statValue hearts" :aria-label="`${stats.lives} lives`">
          <template v-if="heartRows.length">
            <span v-for="(row, i) in heartRows" :key="i" class="heartRow">{{ row }}</span>
          </template>
          <template v-else>—</template>
          <template v-if="tooManyHearts"> ×{{ stats.lives }}</template>
        </span>
      </StatTile>
      <StatTile label="Gold" :value="stats.gold" />
      <StatTile label="Level" :value="stats.level" />
      <StatTile label="Turn" :value="stats.turn" />
      <StatTile label="High score" :value="stats.highScore" />
    </div>

    <ScoreProgress :score="stats.score" :goal-reached="goalReached" />

    <div v-if="reputation" class="reputation" data-testid="reputation">
      <div class="repHead">
        <span class="label">Reputation</span>
        <span class="label repTurn" :class="{ stale }">
          read on turn {{ reputation.turn }}<template v-if="stale"> — stale</template>
        </span>
      </div>
      <div class="repRow">
        <StatTile v-for="[name, value] in factions" :key="name" :label="name">
          <span class="statValue" :class="value < 0 ? 'bad' : value > 0 ? 'good' : ''">
            {{ signed(value) }}
          </span>
        </StatTile>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hud {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: var(--pad);
  border-bottom: var(--rule);
}
/* Collapsed-border cells: the grid's rules are drawn by each cell's right and bottom edge, and the
   container supplies the top and left, so no two rules ever double up. */
.stats,
.repRow {
  display: grid;
  border-top: var(--rule);
  border-left: var(--rule);
}
.stats {
  /* auto-fit, not auto-fill: five tiles share one row instead of orphaning the last. */
  grid-template-columns: repeat(auto-fit, minmax(64px, 1fr));
}
.repRow {
  grid-template-columns: repeat(3, 1fr);
}
.stats > :deep(*),
.repRow > :deep(*) {
  border-right: var(--rule);
  border-bottom: var(--rule);
}
/* The Lives and reputation tiles supply their own markup through StatTile's slot. */
.statValue {
  font-family: var(--font-mono), monospace;
  font-size: var(--text-lg);
  font-weight: 500;
}
.hearts {
  color: var(--red);
  letter-spacing: 2px;
}
.heartRow {
  display: block;
  line-height: 1.2;
}
.reputation {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.repHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap-sm);
}
.repTurn.stale {
  color: var(--red);
  font-style: italic;
}
.good {
  color: var(--ink);
}
.bad {
  color: var(--red);
}
</style>
