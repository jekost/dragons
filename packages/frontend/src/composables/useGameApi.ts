import { useMutation } from '@tanstack/vue-query';
import { computed } from 'vue';
import { api } from '../api/client.js';
import { useGameStore } from '../stores/game.js';
import type { StepResult } from '../types';

/**
 * Wraps the backend calls as TanStack Query mutations and funnels their results into the
 * Pinia store. Server state (in-flight, errors) lives in Query; durable game state in the store.
 */
export function useGameApi() {
  const store = useGameStore();

  // Every mutation reports failures the same way, and every one but startGame applies a StepResult.
  const reportError = (e: Error) => store.setError(e.message);
  const applyStep = (res: StepResult) => store.applyStep(res);

  const startGame = useMutation({
    mutationFn: () => api.startGame(),
    onSuccess: (snap) => store.startFromSnapshot(snap),
    onError: reportError,
  });

  const step = useMutation({
    mutationFn: (gameId: string) => api.autoStep(gameId),
    onSuccess: applyStep,
    onError: (e: Error) => {
      reportError(e);
      store.setStatus('paused'); // stop auto-play on error
    },
  });

  const solve = useMutation({
    mutationFn: (v: { gameId: string; adId: string }) => api.solve(v.gameId, v.adId),
    onSuccess: applyStep,
    onError: reportError,
  });

  const investigate = useMutation({
    mutationFn: (gameId: string) => api.investigate(gameId),
    onSuccess: (res) => store.applyInvestigation(res),
    onError: reportError,
  });

  const buy = useMutation({
    mutationFn: (v: { gameId: string; itemId: string }) => api.buy(v.gameId, v.itemId),
    onSuccess: applyStep,
    onError: reportError,
  });

  // Listing the mutations keeps this correct when one is added, rather than needing another term.
  const isBusy = computed(() =>
    [startGame, step, solve, buy, investigate].some((m) => m.isPending.value),
  );

  return { startGame, step, solve, buy, investigate, isBusy };
}
