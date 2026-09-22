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
    <span class="message">
      <span v-if="recommended" class="star" aria-label="recommended">★</span>
      {{ ad.message }}
      <span v-if="ad.encrypted" class="decoded" title="Was encrypted"> 🔓</span>
    </span>
    <span class="meta">
      <span class="tier" :class="riskClass(ad.probability)">{{ ad.probability }}</span>
      <span class="reward">💰 {{ ad.reward }}</span>
      <span class="expires" title="Turns until it expires">⏳ {{ ad.expiresIn }}</span>
    </span>
  </button>
</template>

<style scoped>
.quest {
  width: 100%;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
  padding: 10px 12px;
}
.quest.recommended {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.message {
  font-size: var(--text-body);
}
.decoded {
  opacity: 0.8;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gap-sm);
  font-size: var(--text-2xs);
}
.tier {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  font-weight: 600;
  border: 1px solid transparent;
}
.tier.safe {
  color: var(--safe);
  border-color: var(--safe);
}
.tier.medium {
  color: var(--medium);
  border-color: var(--medium);
}
.tier.risky {
  color: var(--risky);
  border-color: var(--risky);
}
.tier.deadly {
  color: var(--deadly);
  border-color: var(--deadly);
}
.reward {
  color: var(--accent);
}
.expires {
  color: var(--text-dim);
}
</style>
