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
  return props.goalReached ? 'Game over — target met 🎉' : 'Game over 💀';
});

/** Built from GOAL_SCORE so the badge cannot advertise a target the solver no longer uses. */
const goalLabel = `🎯 ${GOAL_SCORE}+ reached`;
</script>

<template>
  <div class="topRow">
    <div class="badges">
      <span class="badge" :class="status" data-testid="status-badge">{{ statusLabel }}</span>
      <span v-if="goalReached" class="milestone" data-testid="goal-badge">{{ goalLabel }}</span>
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
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
  font-weight: 600;
}
.badge {
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.badge.autoplaying {
  background: rgba(var(--accent-2-rgb), 0.15);
  border-color: var(--accent-2);
  color: var(--accent-2);
}
.badge.over {
  background: rgba(var(--deadly-rgb), 0.15);
  border-color: var(--deadly);
  color: var(--deadly);
}
.badge.paused {
  color: var(--accent);
  border-color: var(--accent);
}
.milestone {
  background: rgba(var(--safe-rgb), 0.15);
  border: 1px solid var(--safe);
  color: var(--safe);
}
.gameId {
  color: var(--text-dim);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}
</style>
