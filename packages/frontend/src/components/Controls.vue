<script setup lang="ts">
import { computed } from 'vue';
import { AUTO_DELAY, useGameStore } from '../stores/game.js';

/**
 * `busy` is the only thing this needs from outside: it belongs to the network layer, which lives
 * in `App`. Everything else — whether a game exists, whether it is over, whether it is playing
 * itself — is read from the store rather than re-derived from a `status` prop, which is how three
 * files ended up with their own spelling of the same question.
 */
const props = defineProps<{ busy: boolean }>();

const emit = defineEmits<{ start: []; toggleAuto: []; step: []; investigate: [] }>();

const store = useGameStore();

const autoToggleDisabled = computed(() => !store.canPlay || props.busy);
// Shared by Step and Investigate: both spend a turn, so both need a live game that is not
// already playing itself.
const stepDisabled = computed(() => !store.canPlay || store.isAutoplaying || props.busy);
const autoLabel = computed(() => (store.isAutoplaying ? '⏸ Pause' : '▶ Auto-play'));

function onDelayInput(event: Event): void {
  store.setAutoDelay(Number((event.target as HTMLInputElement).value));
}
</script>

<template>
  <section class="controls" aria-label="Controls">
    <button v-if="!store.hasGame" class="primary" :disabled="busy" @click="emit('start')">
      ▶ Start game
    </button>
    <template v-else>
      <button
        class="primary"
        data-testid="auto-toggle"
        :disabled="autoToggleDisabled"
        @click="emit('toggleAuto')"
      >
        {{ autoLabel }}
      </button>
      <button data-testid="step-btn" :disabled="stepDisabled" @click="emit('step')">⏭ Step</button>
      <button
        data-testid="investigate-btn"
        :disabled="stepDisabled"
        title="Ask around about your reputation — costs one turn"
        @click="emit('investigate')"
      >
        🔍 Investigate
      </button>
      <button :disabled="busy" @click="store.reset()">↺ New game</button>
    </template>

    <label class="speed">
      <span>Speed</span>
      <input
        type="range"
        :min="AUTO_DELAY.min"
        :max="AUTO_DELAY.max"
        :step="AUTO_DELAY.step"
        :value="store.autoDelayMs"
        aria-label="Auto-play delay"
        @input="onDelayInput"
      />
      <span class="speedValue">{{ store.autoDelayMs }}ms</span>
    </label>
  </section>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 16px;
}
.speed {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--text-dim);
}
.speedValue {
  font-family: var(--font-mono);
  min-width: 52px;
  text-align: right;
}
@media (max-width: 560px) {
  .speed {
    margin-left: 0;
    width: 100%;
  }
  .speed input {
    flex: 1;
  }
}
</style>
