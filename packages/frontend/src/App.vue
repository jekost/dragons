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
    <header class="masthead">
      <h1 class="title">Dragons of Mugloar</h1>
      <p class="dek">
        An auto-playing bot clears {{ GOAL_SCORE }} points, then keeps going for a high score.
      </p>
    </header>

    <Controls
      :busy="isBusy"
      @start="startGame.mutate()"
      @toggle-auto="toggleAuto"
      @step="runStep"
      @investigate="onInvestigate"
    />

    <div v-if="store.error" class="error" role="alert">
      <span class="errorLabel">Error</span>
      <span class="errorText">{{ store.error }}</span>
      <button class="dismiss" aria-label="Dismiss error" @click="store.setError(null)">✕</button>
    </div>

    <main class="grid">
      <div class="col left">
        <Hud
          :state="store.snapshot?.state ?? null"
          :status="store.status"
          :goal-reached="store.goalReached"
          :reputation="store.reputation"
        />
        <Shop v-if="store.snapshot" :disabled="!canAct" @buy="onBuy" />
      </div>

      <div class="col center fill">
        <QuestList
          :ads="store.snapshot?.ads ?? []"
          :recommended-ad-id="store.recommendedAdId"
          :disabled="!canAct"
          @solve="onSolve"
        />
      </div>

      <div class="col right fill">
        <DecisionLog :entries="store.log" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 16px 48px;
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}

.masthead {
  text-align: center;
  border-bottom: var(--rule-heavy);
  padding-bottom: 12px;
}
.title {
  /* Big enough to own the page, capped so the board still starts above the fold on a laptop. */
  font-size: clamp(2.75rem, 8vw, 6rem);
  font-weight: 900;
  line-height: 0.9;
  letter-spacing: -0.03em;
  /* The tight line-height lets the "g" descender crowd the line below; the bottom margin clears it. */
  margin: 8px 0 24px;
}
.dek {
  margin: 0;
  font-style: italic;
  color: var(--text-dim);
}

.error {
  display: flex;
  align-items: center;
  gap: 12px;
  border: var(--rule);
  border-left: 6px solid var(--red);
  background: var(--paper);
  padding: 4px 4px 4px 12px;
}
.errorLabel {
  background: var(--red);
  color: var(--paper);
  font-family: var(--font-sans), sans-serif;
  font-size: var(--text-3xs);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.15em;
  padding: 2px 8px;
  flex-shrink: 0;
}
.errorText {
  flex: 1;
}
.dismiss {
  border: none;
  min-width: 44px;
}

/* Columns: one ruled frame, columns divided by shared rules rather than gaps. */
.grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  border: var(--rule);
  background: var(--paper);
  align-items: stretch;
}
.col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.left {
  grid-column: span 4;
  border-right: var(--rule);
}
.center {
  grid-column: span 5;
  border-right: var(--rule);
}
.right {
  grid-column: span 3;
}
/*
 * The quest list and the log fill their column exactly and scroll inside it. Taking them out of
 * flow is what lets the row height come from the left column (HUD + shop) alone — in flow, a long
 * list would stretch the row to its own full length and never need to scroll. The min-height is a
 * floor for when the left column is short (before a game starts, the shop isn't there yet).
 */
.fill {
  position: relative;
  min-height: 480px;
}
.fill > * {
  position: absolute;
  inset: 0;
}

@media (max-width: 1000px) {
  .left,
  .center {
    grid-column: span 6;
  }
  .center {
    border-right: none;
  }
  .right {
    grid-column: 1 / -1;
    border-top: var(--rule);
  }
  /* Alone on its row, the log has no neighbour to match, so it sizes itself up to a cap. */
  .right {
    min-height: 0;
    --list-max: 620px;
  }
  .right > * {
    position: static;
  }
}
@media (max-width: 680px) {
  .left,
  .center,
  .right {
    grid-column: 1 / -1;
    border-right: none;
  }
  .center,
  .right {
    border-top: var(--rule);
  }
  .center {
    min-height: 0;
    --list-max: 560px;
  }
  .center > * {
    position: static;
  }
}
</style>
