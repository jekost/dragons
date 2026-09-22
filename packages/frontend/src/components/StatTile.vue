<script setup lang="ts">
/**
 * One labelled figure in the HUD's stat grid. The default slot exists for Lives, which renders
 * hearts rather than a plain number; every other tile just passes `value`.
 */
defineProps<{ label: string; value?: number }>();
</script>

<template>
  <div class="stat">
    <span class="label">{{ label }}</span>
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
  padding: 6px 8px;
  min-width: 0;
}
.statValue {
  font-family: var(--font-mono), monospace;
  font-size: var(--text-lg);
  font-weight: 500;
}
</style>
