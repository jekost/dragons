import { isKnownTier, TIER_RANK } from '../types';

export type RiskClass = 'safe' | 'medium' | 'risky' | 'deadly';

/** Map a probability tier to a coarse risk class used for color coding. */
export function riskClass(tier: string): RiskClass {
  if (!isKnownTier(tier)) return 'deadly';
  const rank = TIER_RANK[tier];
  if (rank <= 3) return 'safe';
  if (rank <= 5) return 'medium';
  if (rank <= 8) return 'risky';
  return 'deadly';
}
