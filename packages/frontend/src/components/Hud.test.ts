import type { GameState } from '../types';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import Hud from './Hud.vue';

const state: GameState = {
  gameId: 'abc',
  lives: 3,
  gold: 120,
  level: 2,
  score: 500,
  highScore: 500,
  turn: 7,
};

describe('Hud', () => {
  it('shows the score against the target and the status', () => {
    const wrapper = mount(Hud, { props: { state, status: 'autoplaying' } });
    expect(wrapper.get('[data-testid="score-value"]').text()).toContain('500 / 1000');
    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('Auto-playing');
  });

  it('renders a no-game state gracefully', () => {
    const wrapper = mount(Hud, { props: { state: null, status: 'idle' } });
    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('No game');
    expect(wrapper.get('[data-testid="score-value"]').text()).toContain('0 / 1000');
  });

  it('shows the 1000+ milestone once the goal is reached, without ending the game', () => {
    const wrapper = mount(Hud, {
      props: { state: { ...state, score: 1200 }, status: 'autoplaying', goalReached: true },
    });
    expect(wrapper.find('[data-testid="goal-badge"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="status-badge"]').text()).toContain('Auto-playing');
  });
});
