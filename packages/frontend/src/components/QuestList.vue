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
    <div class="panel-header">
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
/* Rows share rules: each quest draws only its bottom edge. */
/* Scrolls within whatever height the layout gives the panel; `--list-max` caps it only where the
   layout stacks the columns and there is no neighbouring column to take the height from. */
.list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  max-height: var(--list-max, none);
}
.list li {
  border-bottom: var(--rule);
}
</style>
