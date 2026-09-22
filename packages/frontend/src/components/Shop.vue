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
    <div class="header">
      <h2>Shop</h2>
      <span class="gold">💰 {{ gold }}</span>
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
          <span class="name"> <span v-if="recommended" class="star">★</span>{{ item.name }} </span>
          <span class="cost" :class="{ tooExpensive: !affordable }">{{ item.cost }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--pad);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.gold {
  color: var(--accent);
  font-weight: 600;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
}
.item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
}
.item.recommended {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.name {
  font-size: var(--text-sm);
}
.cost {
  font-family: var(--font-mono);
  color: var(--accent);
}
.cost.tooExpensive {
  color: var(--text-dim);
}
</style>
