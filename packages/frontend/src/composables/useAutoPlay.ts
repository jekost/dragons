import { onUnmounted, watch } from 'vue';
import { useGameStore } from '../stores/game.js';

/**
 * Drives the auto-play loop while status is 'autoplaying'. Each tick runs one step, then
 * re-reads live status/delay from the store (so pausing, a win, a loss, or a speed change all
 * take effect immediately). Self-cancelling on status change and unmount.
 */
export function useAutoPlay(runStep: () => Promise<void>): void {
  const store = useGameStore();
  let cancelled = false;
  let timer: ReturnType<typeof setTimeout> | undefined;

  const stop = () => {
    cancelled = true;
    if (timer) clearTimeout(timer);
  };

  watch(
    () => store.status,
    () => {
      stop();
      if (!store.isAutoplaying) return;
      cancelled = false;
      const tick = async () => {
        if (cancelled) return;
        await runStep();
        if (cancelled) return;
        if (store.isAutoplaying) {
          timer = setTimeout(tick, store.autoDelayMs);
        }
      };
      void tick();
    },
  );

  onUnmounted(stop);
}
