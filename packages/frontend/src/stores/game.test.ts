import type { GameSnapshot, GameState, InvestigationResult, StepResult } from '../types';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import { useGameStore } from './game.js';

function makeState(overrides: Partial<GameState> = {}): GameState {
  return {
    gameId: 'g1',
    lives: 3,
    gold: 0,
    level: 0,
    score: 0,
    highScore: 0,
    turn: 0,
    ...overrides,
  };
}

function makeSnapshot(state: GameState): GameSnapshot {
  return { state, shop: [], ads: [], recommendation: { type: 'stop', reason: 'x' } };
}

function solveStep(state: GameState, success: boolean): StepResult {
  return {
    decision: {
      type: 'solve',
      adId: 'a1',
      ad: {
        adId: 'a1',
        message: 'm',
        reward: 10,
        expiresIn: 5,
        encrypted: null,
        probability: 'Sure thing',
      },
      probability: 0.9,
      expectedValue: 9,
      reason: 'solving',
    },
    solve: {
      success,
      lives: state.lives,
      gold: state.gold,
      score: state.score,
      highScore: state.highScore,
      turn: state.turn,
      message: 'ok',
    },
    snapshot: makeSnapshot(state),
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('game store', () => {
  it('startFromSnapshot sets ready status and a start log entry', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState()));
    expect(store.status).toBe('ready');
    expect(store.log).toHaveLength(1);
    expect(store.log[0]?.kind).toBe('start');
  });

  it('stays in play after a successful solve below target', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState()));
    store.setStatus('autoplaying');
    store.applyStep(solveStep(makeState({ score: 200 }), true));
    expect(store.status).toBe('autoplaying');
    expect(store.snapshot?.state.score).toBe(200);
    expect(store.log.at(-1)?.kind).toBe('solve');
  });

  it('flags the 1000 milestone but keeps playing (does not stop at the target)', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState()));
    store.setStatus('autoplaying');
    store.applyStep(solveStep(makeState({ score: 1200 }), true));
    expect(store.goalReached).toBe(true);
    expect(store.status).toBe('autoplaying'); // reaching 1000 must not terminate the game
  });

  it('transitions to over when lives hit zero', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState()));
    store.applyStep(solveStep(makeState({ lives: 0, score: 300 }), false));
    expect(store.status).toBe('over');
    expect(store.log.at(-1)?.kind).toBe('fail');
  });

  it('applyInvestigation records the reputation and the turn it cost', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState({ turn: 11 })));
    const result: InvestigationResult = {
      reputation: { people: 1, state: -2, underworld: 0 },
      snapshot: makeSnapshot(makeState({ turn: 12 })),
    };
    store.applyInvestigation(result);
    expect(store.reputation).toEqual({ people: 1, state: -2, underworld: 0, turn: 12 });
    expect(store.log.at(-1)).toMatchObject({ kind: 'investigate', turn: 12 });
  });

  // A dead game is not investigated upstream, so the backend omits the figures entirely.
  it('applyInvestigation keeps the previous reading when the game is over', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState({ turn: 11 })));
    store.applyInvestigation({
      reputation: { people: 3, state: 3, underworld: 3 },
      snapshot: makeSnapshot(makeState({ turn: 12 })),
    });
    store.applyInvestigation({ snapshot: makeSnapshot(makeState({ turn: 12, lives: 0 })) });
    expect(store.reputation).toMatchObject({ people: 3, state: 3, underworld: 3 });
  });

  it('reset clears the reputation', () => {
    const store = useGameStore();
    store.startFromSnapshot(makeSnapshot(makeState()));
    store.applyInvestigation({
      reputation: { people: 1, state: 0, underworld: 0 },
      snapshot: makeSnapshot(makeState({ turn: 1 })),
    });
    store.reset();
    expect(store.reputation).toBeNull();
  });

  it('records errors in the log', () => {
    const store = useGameStore();
    store.setError('boom');
    expect(store.error).toBe('boom');
    expect(store.log.at(-1)).toMatchObject({ kind: 'error', text: 'boom' });
  });
});
