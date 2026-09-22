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
const hearts = computed(() =>
  stats.value.lives > 0 ? '❤️'.repeat(Math.min(stats.value.lives, MAX_HEARTS)) : '—',
);
</script>

<template>
  <section class="hud" aria-label="Game state">
    <StatusBadges :status="status" :goal-reached="goalReached" :game-id="state?.gameId ?? null" />

    <div class="stats">
      <StatTile label="Lives">
        <span class="statValue hearts" :aria-label="`${stats.lives} lives`">
          {{ hearts }}<template v-if="tooManyHearts"> ×{{ stats.lives }}</template>
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
        <span class="repTitle">Reputation</span>
        <span class="repTurn" :class="{ stale }">
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
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--pad);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  gap: 10px;
}
/* The Lives tile supplies its own markup through StatTile's slot, so it styles its value here. */
.statValue {
  font-size: var(--text-lg);
  font-weight: 600;
}
.hearts {
  letter-spacing: 1px;
}
.reputation {
  border-top: 1px solid var(--border);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.repHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap-sm);
}
.repTitle {
  font-size: var(--text-3xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-dim);
}
.repTurn {
  font-size: var(--text-3xs);
  color: var(--text-dim);
}
.repTurn.stale {
  color: var(--accent);
}
.repRow {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.good {
  color: var(--safe);
}
.bad {
  color: var(--deadly);
}
</style>
