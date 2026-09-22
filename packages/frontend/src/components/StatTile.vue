<script setup lang="ts">
/**
 * One labelled figure in the HUD's stat grid. The default slot exists for Lives, which renders
 * hearts rather than a plain number; every other tile just passes `value`.
 */
defineProps<{ label: string; value?: number }>();
</script>

<template>
  <div class="stat">
    <span class="statLabel">{{ label }}</span>
    <slot>
      <span class="statValue">{{ value }}</span>
    </slot>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.statLabel {
  font-size: var(--text-3xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-dim);
}
.statValue {
  font-size: var(--text-lg);
  font-weight: 600;
}
</style>
