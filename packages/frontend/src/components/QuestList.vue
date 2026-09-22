<script setup lang="ts">
import type { Ad } from '../types';
import { computed } from 'vue';
import QuestCard from './QuestCard.vue';

const props = defineProps<{
  ads: Ad[];
  recommendedAdId: string | null;
  disabled: boolean;
}>();

defineEmits<{ solve: [adId: string] }>();

const adsByRewardDesc = computed(() => [...props.ads].sort((a, b) => b.reward - a.reward));
</script>

<template>
  <section class="panel" aria-label="Quests">
    <div class="header">
      <h2>Quests</h2>
      <span class="count">{{ adsByRewardDesc.length }}</span>
    </div>

    <p v-if="adsByRewardDesc.length === 0" class="empty">No quests available.</p>
    <ul v-else class="list">
      <li v-for="ad in adsByRewardDesc" :key="ad.adId">
        <QuestCard
          :ad="ad"
          :recommended="ad.adId === recommendedAdId"
          :disabled="disabled"
          @solve="$emit('solve', $event)"
        />
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
.empty {
  color: var(--text-dim);
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
  overflow-y: auto;
  max-height: 420px;
}
</style>
