# Level Experiment

_Generated 2026-09-18 from 40 live games, 791 logged attempts._

**Question.** Does the dragon's level change its odds, or does the game scale difficulty so the two cancel out?

**Design.** Every game is assigned an upgrade arm — `NONE` never buys an upgrade, `FAST` buys the cheapest affordable one at every chance — round-robin against a target tier drawn from the mid band (Quite likely, Hmmm...., Gamble, Risky, Rather detrimental). Because the arm is *assigned* rather than earned, level is no longer glued to the turn counter, and the two can be told apart. Potions are allowed in both arms: they keep games alive for comparable lengths without touching level.

Raw rows are in `attempts.csv` — re-analyse from there rather than re-running.

## 1. Does levelling help? (`FAST` vs `NONE`, the causal contrast)

| Tier | FAST | NONE |
| --- | ---: | ---: |
| Gamble | 60% (35/58) | 60% (50/83) |
| Hmmm.... | 58% (39/67) | 60% (46/77) |
| Piece of cake | 96% (22/23) | 100% (25/25) |
| Quite likely | 78% (47/60) | 75% (69/92) |
| Rather detrimental | 37% (15/41) | 29% (23/78) |
| Risky | 48% (25/52) | 46% (33/71) |
| Sure thing | 100% (18/18) | 81% (30/37) |
| Walk in the park | 100% (2/2) | 100% (7/7) |

A real level effect shows up as `FAST` beating `NONE` on the same row. Compare like for like — see table 2 before drawing a conclusion, since the arms do not spend their turns identically.

## 2. Does the game scale with time? (arm `NONE` only, where level never moves)

| Tier | 00-09 | 10-19 | 20+ |
| --- | ---: | ---: | ---: |
| Gamble | 64% (14/22) | 40% (8/20) | 68% (28/41) |
| Hmmm.... | 59% (17/29) | 60% (9/15) | 61% (20/33) |
| Piece of cake | 100% (5/5) | 100% (8/8) | 100% (12/12) |
| Quite likely | 85% (34/40) | 62% (13/21) | 71% (22/31) |
| Rather detrimental | 25% (5/20) | 21% (3/14) | 34% (15/44) |
| Risky | 42% (8/19) | 36% (5/14) | 53% (20/38) |
| Sure thing | 100% (18/18) | 100% (10/10) | 22% (2/9) |
| Walk in the park | 100% (1/1) | 100% (1/1) | 100% (5/5) |

Level is fixed down every row here, so a decline left-to-right is difficulty scaling with the turn counter — and would mean late-game ads are harder than their label implies.

## 3. Success by level reached (observational — read with care)

| Tier | L0 | L1-2 | L3-4 | L5+ |
| --- | ---: | ---: | ---: | ---: |
| Gamble | 61% (66/109) | 73% (8/11) | 38% (3/8) | 62% (8/13) |
| Hmmm.... | 56% (57/101) | 60% (12/20) | 100% (3/3) | 65% (13/20) |
| Piece of cake | 100% (32/32) | 90% (9/10) | 100% (1/1) | 100% (5/5) |
| Quite likely | 75% (84/112) | 75% (15/20) | 67% (4/6) | 93% (13/14) |
| Rather detrimental | 31% (28/90) | 38% (5/13) | 33% (2/6) | 30% (3/10) |
| Risky | 48% (44/91) | 47% (8/17) | 67% (4/6) | 22% (2/9) |
| Sure thing | 85% (41/48) | 100% (7/7) | — | — |
| Walk in the park | 100% (7/7) | — | — | 100% (2/2) |

⚠️ This table pools both arms, so level still correlates with turn number: a high-level attempt is also a late attempt. It cannot separate the two on its own — it is here because it is the table everyone expects to see, and tables 1 and 2 are what actually answer the question.

## Turn budget spent

| Arm | Attempts | Games | Overall success |
| --- | ---: | ---: | ---: |
| NONE | 470 | 20 | 60% (283/470) |
| FAST | 321 | 20 | 63% (203/321) |

Final scores: [1502, 1288, 82, 1279, 247, 110, 135, 2036, 113, 249, 1387, 1855, 0, 1369, 195, 476, 1889, 584, 110, 149, 30, 216, 1821, 291, 242, 149, 2680, 253, 231, 1257, 1615, 28, 2028, 42, 1553, 342, 321, 0, 0, 2121]
