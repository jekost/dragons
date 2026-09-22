# Solver Benchmark

_Generated 2026-09-21 from 40 live games, turn cap 100._

Unlike the other runs here this is a **score** run: it drives `StrategyEngine.decide()` and obeys it, so the numbers describe the solver rather than the game. Arms alternate game by game and differ in one thing only — whether an ad priced above its tier's reward ceiling is valued at the measured rate (`CEILINGS`) or at its label (`LABEL_ONLY`, the previous behaviour).

| Arm | Games | Median | Mean | Min | Max | Reached 1000 | Died |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| CEILINGS | 20 | 2861 | 2851 | 20 | 3984 | 19 (95%) | 16 |
| LABEL_ONLY | 20 | 3097 | 2928 | 899 | 4317 | 19 (95%) | 12 |

**Median -236 points in favour of CEILINGS; goal rate +0pp.**

Scores are heavy-tailed — a game that survives grinds on until the cap — so read the median and the goal rate, not the mean. With a few dozen games per arm only a large gap is worth believing; a small one is noise. Raw rows: `benchmark.csv`.
