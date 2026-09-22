<script setup lang="ts">
import type { Ad } from '../types';
import { riskClass } from '../lib/tiers.js';

defineProps<{ ad: Ad; recommended: boolean; disabled: boolean }>();
defineEmits<{ solve: [adId: string] }>();
</script>

<template>
  <button
    class="quest"
    :class="{ recommended }"
    :disabled="disabled"
    title="Solve this quest"
    @click="$emit('solve', ad.adId)"
  >
    <span v-if="recommended" class="pick label"><span class="star">★</span>Recommended</span>
    <span class="message">{{ ad.message }}</span>
    <span class="meta">
      <span class="tier" :class="riskClass(ad.probability)">{{ ad.probability }}</span>
      <span class="figure"><span class="label">Reward</span> {{ ad.reward }}</span>
      <span class="figure" title="Turns until it expires">
        <span class="label">Expires</span> {{ ad.expiresIn }}
      </span>
      <span v-if="ad.encrypted" class="label decoded" title="Was encrypted">Decoded</span>
    </span>
  </button>
</template>

<style scoped>
/* Quests are articles, not controls: override the global button's uppercase sans and inversion. */
.quest {
  width: 100%;
  min-height: 0;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
  padding: 12px;
  border: none;
  background: var(--paper);
  font-family: var(--font-body), serif;
  font-size: var(--text-body);
  font-weight: 400;
  text-transform: none;
  letter-spacing: normal;
}
.quest:hover:not(:disabled) {
  background: var(--neutral-100);
  color: var(--ink);
}
.quest.recommended {
  outline: 2px solid var(--ink);
  outline-offset: -2px;
}
.pick {
  color: var(--ink);
}
.message {
  line-height: 1.45;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
}
.figure {
  font-family: var(--font-mono), monospace;
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}
.figure .label {
  margin-right: 2px;
}
.decoded {
  border: 1px dotted var(--text-dim);
  padding: 0 4px;
}

/* Danger by colour, green to red, as filled badges with ink text so the pale hues stay readable
   on paper. The tier name is always printed too, so colour is never the only signal. */
.tier {
  font-family: var(--font-sans), sans-serif;
  font-size: var(--text-3xs);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  padding: 2px 6px;
  border: var(--rule);
  color: var(--ink);
}
.tier.safe {
  background: var(--safe);
}
.tier.medium {
  background: var(--medium);
}
.tier.risky {
  background: var(--risky);
}
.tier.deadly {
  background: var(--deadly);
}
</style>
