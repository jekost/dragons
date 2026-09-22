<script setup lang="ts">
import { computed } from 'vue';
import { GOAL_SCORE, type GameStatus } from '../stores/game.js';

const props = defineProps<{
  status: GameStatus;
  goalReached: boolean;
  gameId: string | null;
}>();

const STATUS_LABEL: Record<GameStatus, string> = {
  idle: 'No game',
  ready: 'Ready',
  autoplaying: 'Auto-playing',
  paused: 'Paused',
  over: 'Game over',
};

/** Game over is the one status whose wording depends on whether the target was met. */
const statusLabel = computed(() => {
  if (props.status !== 'over') return STATUS_LABEL[props.status];
  return props.goalReached ? 'Game over — target met' : 'Game over';
});

/** Built from GOAL_SCORE so the badge cannot advertise a target the solver no longer uses. */
const goalLabel = `${GOAL_SCORE}+ reached`;
</script>

<template>
  <div class="topRow">
    <div class="badges">
      <span class="badge" :class="status" data-testid="status-badge">{{ statusLabel }}</span>
      <span v-if="goalReached" class="milestone" data-testid="goal-badge">
        <span class="star" aria-hidden="true">★</span>{{ goalLabel }}
      </span>
    </div>
    <span v-if="gameId" class="gameId">#{{ gameId }}</span>
  </div>
</template>

<style scoped>
.topRow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gap-sm);
}
.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.badge,
.milestone {
  padding: 3px 8px;
  border: var(--rule);
  font-family: var(--font-sans), sans-serif;
  font-size: var(--text-3xs);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}
.badge.autoplaying {
  background: var(--ink);
  color: var(--paper);
}
.badge.paused {
  border-style: dashed;
}
.badge.over {
  background: var(--red);
  border-color: var(--red);
  color: var(--paper);
}
.milestone .star {
  margin-right: 4px;
}
.gameId {
  font-family: var(--font-mono), monospace;
  font-size: var(--text-3xs);
  color: var(--text-dim);
}
</style>
