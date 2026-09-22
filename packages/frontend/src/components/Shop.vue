<script setup lang="ts">
import { computed } from 'vue';
import { useGameStore } from '../stores/game.js';

/**
 * `disabled` is the only prop: it depends on the network layer, which lives in `App`. The items,
 * the gold and the recommendation are all read from the store rather than passed in — `App` was
 * otherwise taking one snapshot apart just for this component to put it back together.
 */
defineProps<{ disabled: boolean }>();

const emit = defineEmits<{ buy: [itemId: string] }>();

const store = useGameStore();

const gold = computed(() => store.snapshot?.state.gold ?? 0);

/** Each row's derived state, decided once here rather than three times per row in the template. */
const rows = computed(() =>
  (store.snapshot?.shop ?? []).map((item) => ({
    item,
    affordable: gold.value >= item.cost,
    recommended: item.id === store.recommendedItemId,
  })),
);
</script>

<template>
  <section class="panel" aria-label="Shop">
    <div class="panel-header">
      <h2>Shop</h2>
      <span class="gold"><span class="label">Gold</span> {{ gold }}</span>
    </div>
    <ul class="list">
      <li v-for="{ item, affordable, recommended } in rows" :key="item.id">
        <button
          class="item"
          :class="{ recommended }"
          :disabled="disabled || !affordable"
          :title="affordable ? `Buy ${item.name}` : 'Not enough gold'"
          @click="emit('buy', item.id)"
        >
          <span class="name"><span v-if="recommended" class="star">★</span>{{ item.name }}</span>
          <span class="cost" :class="{ tooExpensive: !affordable }">{{ item.cost }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.panel {
  border-bottom: var(--rule);
}
.gold {
  font-family: var(--font-mono), monospace;
  font-size: var(--text-md);
  font-weight: 500;
}
/* A price list: ruled rows, name left, mono price right, the row inverting on hover. */
.list li + li {
  border-top: var(--rule);
}
.item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 4px;
  border: none;
  font-family: var(--font-body), serif;
  font-size: var(--text-sm);
  font-weight: 400;
  text-transform: none;
  letter-spacing: normal;
}
.item.recommended {
  outline: 2px solid var(--ink);
  outline-offset: -2px;
}
/* Dimmed by colour, not the global 40% opacity, which would drop the text below AA contrast. */
.item:disabled {
  opacity: 1;
  color: var(--text-dim);
}
.cost {
  font-family: var(--font-mono), monospace;
  font-variant-numeric: tabular-nums;
}
.cost.tooExpensive {
  text-decoration: line-through;
}
</style>
