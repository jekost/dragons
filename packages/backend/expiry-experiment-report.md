# Expiry Experiment

_Generated 2026-09-21 from 40 live games — 1613 attempts (699 paired), 16130 board rows. Seed 20260918._

**Does an ad about to expire succeed at the rate its label advertises?** Every number in `probabilities.json` was measured on ads with 3+ turns left, so the solver applies those odds to near-expiry ads on no evidence at all. Each turn this run finds a tier holding both an ad on its last turn (expiresIn 1) and one with 3+ left, then flips a seeded coin for which to attempt. Same label, same board, same turn.

Ads between the two thresholds are neither arm: the gap is what keeps this a contrast rather than a comparison of one turn against two. Level is pinned at 0 — no upgrades — because levelling has its own experiment.

Raw rows: `expiry-attempts.csv` (one per solve) and `expiry-board.csv` (one per ad offered, unfiltered — the first record this repo has of ads below 3 turns left).

## EXPIRING vs FRESH, same tier

| Tier | EXPIRING | FRESH |
| --- | ---: | ---: |
| Gamble | 61% (25/41) | 62% (26/42) |
| Hmmm.... | 55% (21/38) | 69% (33/48) |
| Impossible | 0% (0/2) | 0% (0/1) |
| Piece of cake | 79% (33/42) | 71% (36/51) |
| Playing with fire | 28% (5/18) | 45% (9/20) |
| Quite likely | 63% (33/52) | 76% (34/45) |
| Rather detrimental | 37% (13/35) | 42% (10/24) |
| Risky | 50% (13/26) | 51% (18/35) |
| Suicide mission | 8% (3/37) | 5% (1/22) |
| Sure thing | 20% (2/10) | 19% (3/16) |
| Walk in the park | 88% (38/43) | 80% (41/51) |

**Pooled: EXPIRING 54% (186/344) vs FRESH 59% (211/355)** — difference -5.4pp.

A flat table means the label already tells the whole story and the solver is right to ignore expiry. EXPIRING sitting below FRESH on most rows would mean the shipped odds are optimistic for near-expiry ads, and the solver should either discount them or skip them.

## Against the shipped odds

| Tier | Shipped | EXPIRING | FRESH |
| --- | ---: | ---: | ---: |
| Sure thing | 0.98 | 20% (2/10) | 19% (3/16) |
| Piece of cake | 0.89 | 79% (33/42) | 71% (36/51) |
| Walk in the park | 0.92 | 88% (38/43) | 80% (41/51) |
| Quite likely | 0.77 | 63% (33/52) | 76% (34/45) |
| Hmmm.... | 0.61 | 55% (21/38) | 69% (33/48) |
| Gamble | 0.55 | 61% (25/41) | 62% (26/42) |
| Risky | 0.41 | 50% (13/26) | 51% (18/35) |
| Rather detrimental | 0.42 | 37% (13/35) | 42% (10/24) |
| Playing with fire | 0.17 | 28% (5/18) | 45% (9/20) |
| Suicide mission | 0.17 | 8% (3/37) | 5% (1/22) |
| Impossible | 0.08 | 0% (0/2) | 0% (0/1) |

The shipped column is the 3+ baseline from an earlier run, so FRESH here is also a drift check on it.

## Confounds

| Arm | Median reward | Attempts |
| --- | ---: | ---: |
| EXPIRING | 80 (n=344) | 344 |
| FRESH | 72 (n=355) | 355 |

Reward tracks difficulty, so the arms have to be offered comparable money for the contrast to be about expiry. A large gap here weakens everything above it.

## Pairing rate by turn window

| Turn window | Attempts | Paired |
| --- | ---: | ---: |
| 00-09 | 393 | 85 |
| 10-19 | 356 | 163 |
| 20+ | 864 | 451 |

An ad needs 6 unsolved turns to reach its last one, so early turns cannot pair. A pairing rate that stays low in the later windows means the run bought less evidence than its game count suggests.

Final scores: [1857, 2173, 1051, 1361, 1753, 2265, 1937, 1107, 2567, 1626, 1662, 1887, 2291, 2440, 940, 933, 886, 1702, 2239, 1700, 1685, 2329, 1183, 2240, 1313, 2189, 966, 2459, 974, 2217, 867, 1868, 1129, 1863, 1160, 2274, 1871, 2215, 1520, 2033]
