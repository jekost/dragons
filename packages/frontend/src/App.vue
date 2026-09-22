<script setup lang="ts">
import { computed } from 'vue';
import Controls from './components/Controls.vue';
import DecisionLog from './components/DecisionLog.vue';
import Hud from './components/Hud.vue';
import QuestList from './components/QuestList.vue';
import Shop from './components/Shop.vue';
import { useAutoPlay } from './composables/useAutoPlay.js';
import { useGameApi } from './composables/useGameApi.js';
import { GOAL_SCORE, useGameStore } from './stores/game.js';

const store = useGameStore();
const { startGame, step, solve, buy, investigate, isBusy } = useGameApi();

async function runStep(): Promise<void> {
  await withGame(async (id) => {
    try {
      await step.mutateAsync(id);
    } catch {
      /* error is surfaced via the store; auto-play pauses */
    }
  });
}

useAutoPlay(runStep);

/** Manual moves need a live game that is not already being played for you, and a free connection. */
const canAct = computed(
  () => store.canPlay && !store.isAutoplaying && !isBusy.value && !!store.gameId,
);

/** Runs `action` only when a game exists — the guard three handlers each repeated. */
async function withGame(action: (gameId: string) => unknown): Promise<void> {
  if (store.gameId) await action(store.gameId);
}

function toggleAuto(): void {
  store.setStatus(store.isAutoplaying ? 'paused' : 'autoplaying');
}
function onSolve(adId: string): void {
  void withGame((gameId) => solve.mutate({ gameId, adId }));
}
function onBuy(itemId: string): void {
  void withGame((gameId) => buy.mutate({ gameId, itemId }));
}
function onInvestigate(): void {
  void withGame((gameId) => investigate.mutate(gameId));
}
</script>

<template>
  <div class="app">
    <header class="head">
      <div>
        <h1 class="title">🐉 Dragons of Mugloar</h1>
        <p class="subtitle">
          Auto-playing bot - clear {{ GOAL_SCORE }} points, then keep going for a high score.
        </p>
      </div>
    </header>

    <Controls
      :busy="isBusy"
      @start="startGame.mutate()"
      @toggle-auto="toggleAuto"
      @step="runStep"
      @investigate="onInvestigate"
    />

    <div v-if="store.error" class="error" role="alert">
      <span>{{ store.error }}</span>
      <button aria-label="Dismiss error" @click="store.setError(null)">✕</button>
    </div>

    <main class="grid">
      <div class="left">
        <Hud
          :state="store.snapshot?.state ?? null"
          :status="store.status"
          :goal-reached="store.goalReached"
          :reputation="store.reputation"
        />
        <Shop v-if="store.snapshot" :disabled="!canAct" @buy="onBuy" />
      </div>

      <div class="center">
        <QuestList
          :ads="store.snapshot?.ads ?? []"
          :recommended-ad-id="store.recommendedAdId"
          :disabled="!canAct"
          @solve="onSolve"
        />
      </div>

      <div class="right">
        <DecisionLog :entries="store.log" />
      </div>
    </main>

    <footer class="foot">
      Strategy runs server-side; probabilities come from the committed characterization data.
    </footer>
  </div>
</template>

<style scoped>
.app {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px 48px;
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  font-size: 1.5rem;
}
.subtitle {
  margin: 4px 0 0;
  color: var(--text-dim);
  font-size: var(--text-sm);
}
.error {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: rgba(var(--deadly-rgb), 0.12);
  border: 1px solid var(--deadly);
  color: var(--deadly);
  border-radius: var(--radius);
  padding: 10px 14px;
}
.error button {
  border: none;
  background: transparent;
  color: inherit;
}
.grid {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(320px, 1.3fr) minmax(280px, 1fr);
  gap: var(--gap);
  align-items: start;
}
.left,
.center,
.right {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
  min-width: 0;
}
.foot {
  color: var(--text-dim);
  font-size: var(--text-2xs);
  text-align: center;
}
@media (max-width: 1000px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
  .right {
    grid-column: 1 / -1;
  }
}
@media (max-width: 680px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
