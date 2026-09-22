/**
 * TypeScript types describing the Java backend's JSON API. The frontend is fully typed against
 * this contract. (All game logic lives in the Java backend; these are just shapes.)
 */

/** The text risk tiers the API returns for an ad, ordered safest → deadliest. */
export const PROBABILITY_TIERS = [
  'Sure thing',
  'Piece of cake',
  'Walk in the park',
  'Quite likely',
  'Hmmm....',
  'Gamble',
  'Risky',
  'Rather detrimental',
  'Playing with fire',
  'Suicide mission',
  'Impossible',
] as const;

export type ProbabilityTier = (typeof PROBABILITY_TIERS)[number];

/** Rank 0 = safest. Handy for sorting / color-coding. */
export const TIER_RANK: Record<ProbabilityTier, number> = Object.fromEntries(
  PROBABILITY_TIERS.map((t, i) => [t, i]),
) as Record<ProbabilityTier, number>;

export function isKnownTier(value: string): value is ProbabilityTier {
  return (PROBABILITY_TIERS as readonly string[]).includes(value);
}

/** Encryption flag on a message: null = plaintext, 1 = Base64, 2 = ROT13 (decoded server-side). */
export type EncryptionType = null | 1 | 2;

/** A decoded quest, as served by the backend. */
export interface Ad {
  adId: string;
  message: string;
  reward: number;
  expiresIn: number;
  encrypted: EncryptionType;
  probability: ProbabilityTier | string;
}

export interface ShopItem {
  id: string;
  name: string;
  cost: number;
}

/** Full game state. `level` is only ever updated from buy responses. */
export interface GameState {
  gameId: string;
  lives: number;
  gold: number;
  level: number;
  score: number;
  highScore: number;
  turn: number;
}

/** Response from POST /solve/{adId}. Note: no `level` field. */
export interface SolveResponse {
  success: boolean;
  lives: number;
  gold: number;
  score: number;
  highScore: number;
  turn: number;
  message: string;
}

/** Response from POST /shop/buy/{itemId}. Includes `level`. */
export interface BuyResponse {
  shoppingSuccess: boolean;
  gold: number;
  lives: number;
  level: number;
  turn: number;
}

/**
 * Standing with the three factions, from POST /investigate/reputation. Values are **fractional**
 * (they climb by roughly a tenth per solved quest) and go negative; the backend rounds each to two
 * decimals.
 *
 * The response carries nothing else — no lives, gold or turn — even though the call spends a turn
 * like any other move, which is why the backend hands back a fresh snapshot alongside it.
 */
export interface Reputation {
  people: number;
  state: number;
  underworld: number;
}

/** Result of a reputation lookup. `reputation` is omitted when the game was already over. */
export interface InvestigationResult {
  reputation?: Reputation;
  snapshot: GameSnapshot;
}

/**
 * The score the assignment requires. A milestone, not a stop condition: the bot plays past it to
 * maximize the final score and only ends when it runs out of lives.
 */
export const GOAL_SCORE = 1000;

/**
 * A strategy decision (discriminated union on `type`). The backend omits the fields that don't
 * apply to a given branch.
 */
export type Decision =
  | {
      type: 'solve';
      adId: string;
      ad: Ad;
      probability: number;
      expectedValue: number;
      reason: string;
    }
  | { type: 'buy'; itemId: string; item: ShopItem; reason: string }
  | { type: 'stop'; reason: string };

/**
 * The full picture of a game at a point in time. `recommendation` is what the strategy engine
 * would do next given this snapshot.
 */
export interface GameSnapshot {
  state: GameState;
  shop: ShopItem[];
  ads: Ad[];
  recommendation: Decision;
}

/** Result of executing one action (auto-step or a manual move). */
export interface StepResult {
  decision: Decision;
  solve?: SolveResponse;
  buy?: BuyResponse;
  snapshot: GameSnapshot;
}

/** Standard error body returned by the backend. */
export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    status: number;
  };
}
