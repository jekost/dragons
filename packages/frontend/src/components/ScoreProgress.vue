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
      <span class="label">{{ heading }}</span>
      <span data-testid="score-value">
        <strong>{{ score }}</strong> / {{ GOAL_SCORE }}
        <span v-if="goalReached" class="check" aria-label="goal met">★</span>
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
  align-items: baseline;
  gap: var(--gap-sm);
  font-family: var(--font-mono), monospace;
  font-size: var(--text-sm);
}
.scoreHeader strong {
  font-family: var(--font-serif), serif;
  font-size: 2rem;
  font-weight: 900;
  line-height: 1;
  /* Playfair's default old-style figures drop below the mono "/ 1000" beside them. */
  font-variant-numeric: lining-nums;
}
.check {
  color: var(--red);
}
.progressTrack {
  height: 12px;
  border: var(--rule);
  background: var(--muted);
}
.progressFill {
  height: 100%;
  background: var(--ink);
  transition: width 0.3s ease-out;
}
</style>
