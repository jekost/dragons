<script setup lang="ts">
import { computed } from 'vue';
import { GOAL_SCORE } from '../stores/game.js';

const props = defineProps<{ score: number; goalReached: boolean }>();

const FULL_PERCENT = 100;

/** Clamped: the bot plays on past the goal, so the bar fills and stays full. */
const progressPercent = computed(() =>
  Math.min(FULL_PERCENT, (props.score / GOAL_SCORE) * FULL_PERCENT),
);

const heading = computed(() =>
  props.goalReached ? 'Score (goal met — playing for extra)' : 'Score',
);
</script>

<template>
  <div class="scoreBlock">
    <div class="scoreHeader">
      <span>{{ heading }}</span>
      <span data-testid="score-value">
        <strong>{{ score }}</strong> / {{ GOAL_SCORE }}
        <span v-if="goalReached" class="check">✓</span>
      </span>
    </div>
    <div class="progressTrack">
      <div
        class="progressFill"
        :class="{ complete: goalReached }"
        :style="{ width: `${progressPercent}%` }"
      />
    </div>
  </div>
</template>

<style scoped>
.scoreBlock {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.scoreHeader {
  display: flex;
  justify-content: space-between;
  gap: var(--gap-sm);
  font-size: var(--text-sm);
  color: var(--text-dim);
}
.scoreHeader strong {
  color: var(--text);
  font-size: var(--text-md);
}
.check {
  color: var(--safe);
  font-weight: 700;
}
.progressTrack {
  height: 10px;
  background: var(--surface-2);
  border-radius: var(--radius-pill);
  overflow: hidden;
}
.progressFill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-2), var(--safe));
  transition: width 0.3s ease;
}
.progressFill.complete {
  background: var(--safe);
}
</style>
