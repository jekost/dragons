<script setup lang="ts">
import { ref, watch } from 'vue';
import type { LogEntry } from '../stores/game.js';

const props = defineProps<{ entries: LogEntry[] }>();

const scrollAnchor = ref<HTMLDivElement | null>(null);

/**
 * Keep the newest line in view. `flush: 'post'` runs the callback after the DOM has been patched,
 * which is what the old `await nextTick()` inside the callback was working around.
 */
watch(
  () => props.entries.length,
  () => scrollAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'end' }),
  { flush: 'post' },
);
</script>

<template>
  <section class="panel" aria-label="Decision log">
    <div class="header">
      <h2>Decision log</h2>
      <span class="count">{{ entries.length }}</span>
    </div>
    <div class="scroll">
      <p v-if="entries.length === 0" class="empty">Start a game to see the bot's decisions.</p>
      <ul v-else class="list">
        <li v-for="entry in entries" :key="entry.id" class="entry" :class="entry.kind">
          <span class="turn">T{{ entry.turn }}</span>
          <span class="text">{{ entry.text }}</span>
        </li>
      </ul>
      <div ref="scrollAnchor" />
    </div>
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
  min-height: 0;
}
.header {
  display: flex;
  align-items: center;
  gap: var(--gap-sm);
}
.count {
  color: var(--text-dim);
  background: var(--surface-2);
  border-radius: var(--radius-pill);
  padding: 1px 8px;
  font-size: var(--text-2xs);
}
.scroll {
  overflow-y: auto;
  max-height: 460px;
}
.empty {
  color: var(--text-dim);
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.entry {
  display: flex;
  gap: var(--gap-sm);
  padding: 6px 8px;
  border-radius: var(--radius-xs);
  font-size: var(--text-xs);
  border-left: 3px solid var(--border);
  background: var(--surface-2);
}
.turn {
  color: var(--text-dim);
  font-family: var(--font-mono);
  flex-shrink: 0;
}
.entry.solve {
  border-left-color: var(--safe);
}
.entry.fail {
  border-left-color: var(--deadly);
}
.entry.buy {
  border-left-color: var(--accent-2);
}
.entry.stop {
  border-left-color: var(--accent);
}
.entry.investigate {
  border-left-color: var(--accent-2);
}
.entry.error {
  border-left-color: var(--deadly);
  color: var(--deadly);
}
.entry.start {
  border-left-color: var(--text-dim);
}
</style>
