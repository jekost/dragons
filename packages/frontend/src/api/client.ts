import type { ApiErrorBody, GameSnapshot, InvestigationResult, StepResult } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api';

async function request<T>(path: string, method: 'GET' | 'POST' = 'GET'): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { method, headers: { Accept: 'application/json' } });
  } catch {
    throw new Error('Cannot reach the server. Is the backend running?');
  }
  if (!res.ok) {
    let message = `Request failed (${res.status}).`;
    try {
      const body = (await res.json()) as ApiErrorBody;
      if (body?.error?.message) message = body.error.message;
    } catch {
      /* non-JSON error body — keep the default message */
    }
    throw new Error(message);
  }
  return (await res.json()) as T;
}

/** Typed client for our own backend. */
export const api = {
  startGame: () => request<GameSnapshot>('/games', 'POST'),
  getSnapshot: (gameId: string) => request<GameSnapshot>(`/games/${gameId}`),
  autoStep: (gameId: string) => request<StepResult>(`/games/${gameId}/auto-step`, 'POST'),
  // A POST because it spends a turn upstream, same as a solve — it is a move, not a read.
  investigate: (gameId: string) =>
    request<InvestigationResult>(`/games/${gameId}/investigate`, 'POST'),
  solve: (gameId: string, adId: string) =>
    request<StepResult>(`/games/${gameId}/solve/${adId}`, 'POST'),
  buy: (gameId: string, itemId: string) =>
    request<StepResult>(`/games/${gameId}/buy/${itemId}`, 'POST'),
};
