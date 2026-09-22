<script setup lang="ts">
import { ref, watch } from 'vue';
import type { LogEntry } from '../stores/game.js';

const props = defineProps<{ entries: LogEntry[] }>();

const scroller = ref<HTMLDivElement | null>(null);

/** How close to the bottom still counts as "following along", in px. */
const FOLLOW_SLACK = 24;

/**
 * Keep the newest line in view by scrolling the log's own box — never `scrollIntoView`, which also
 * scrolls every ancestor and so dragged the whole page along on each auto-played turn.
 *
 * It only follows when the reader was already at the bottom: scrolled up to read an older entry,
 * they stay put. Whether they were at the bottom has to be read *before* the new entry lands
 * (`flush: 'pre'`), because afterwards the box is one entry taller and nobody is at the bottom.
 */
let following = true;
watch(
  () => props.entries.length,
  () => {
    const el = scroller.value;
    following = !el || el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_SLACK;
  },
  { flush: 'pre' },
);
watch(
  () => props.entries.length,
  () => {
    const el = scroller.value;
    if (el && following) el.scrollTop = el.scrollHeight;
  },
  { flush: 'post' },
);
</script>

<template>
  <section class="panel inverted" aria-label="Decision log">
    <div class="panel-header">
      <h2>Decision log</h2>
      <span class="count">{{ entries.length }}</span>
    </div>
    <div ref="scroller" class="scroll">
      <p v-if="entries.length === 0" class="empty">Start a game to see the bot's decisions.</p>
      <ul v-else class="list">
        <li v-for="entry in entries" :key="entry.id" class="entry" :class="entry.kind">
          <span class="turn">T{{ entry.turn }}</span>
          <span v-if="entry.kind === 'fail'" class="flag">Failed</span>
          <span v-else-if="entry.kind === 'error'" class="flag">Error</span>
          <span class="text">{{ entry.text }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
/* The one inverted column: ink ground, paper type. Red never appears
   as text on the black (too little contrast); failures get a red rule and a paper-on-red flag. */
.inverted {
  background: var(--ink);
  color: var(--paper);
}
.inverted .panel-header {
  border-bottom-color: var(--paper);
}
.inverted .count {
  border-color: var(--paper);
}
.inverted .empty {
  color: var(--neutral-400);
}
.scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  max-height: var(--list-max, none);
}
.entry {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px var(--gap-sm);
  padding: 6px 0 6px 10px;
  font-family: var(--font-mono), monospace;
  font-size: var(--text-2xs);
  line-height: 1.45;
  border-left: 3px solid var(--neutral-600);
}
.entry + .entry {
  border-top: 1px solid #333333;
}
.turn {
  color: var(--neutral-400);
  flex-shrink: 0;
}
.text {
  flex: 1 1 12ch;
  min-width: 0;
}
.flag {
  background: var(--red);
  color: var(--paper);
  font-family: var(--font-sans), sans-serif;
  font-size: var(--text-3xs);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  padding: 0 5px;
}
.entry.solve {
  border-left-color: var(--paper);
}
.entry.buy,
.entry.investigate {
  border-left-color: var(--neutral-400);
}
.entry.stop {
  border-left-style: dashed;
  border-left-color: var(--paper);
}
.entry.fail,
.entry.error {
  border-left-color: var(--red);
}
.entry.start {
  border-left-color: var(--neutral-600);
}
</style>
