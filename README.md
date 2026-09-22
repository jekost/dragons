# 🐉 Dragons of Mugloar — Fullstack Auto-Player

A fullstack app that plays [Dragons of Mugloar](https://dragonsofmugloar.com/) and **reliably
reaches 1000+ points**. A **Vue 3** SPA visualizes the game live while a **Java (Spring Boot)**
backend runs the strategy engine (the "program") — solving quests, managing lives and gold, and
driving the game either fully autonomously or under manual control.

> **Stack:** Java 25 + Spring Boot backend, Vue 3 + TypeScript frontend — matching the team's
> stack. The design leaves clean seams for the rest of it: the in-memory session store swaps
> directly for **Redis**, and turn events could be published over **RabbitMQ** — without
> over-building a take-home.

> **Result:** the solver clears 1000 points in **38 of 40 games** and scores a median of
> **~2,900** (measured live by `npm run benchmark` — see
> [Characterization](#characterization-measuring-the-hidden-mechanics)). **1000 is a milestone,
> not a finish line** — the bot plays on to maximize the final score and only stops when it runs
> out of lives.

---

## Table of contents

- [Quick start](#quick-start)
- [What it does](#what-it-does)
- [Architecture](#architecture)
- [The solver strategy](#the-solver-strategy)
- [Characterization: measuring the hidden mechanics](#characterization-measuring-the-hidden-mechanics)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Tech choices](#tech-choices)
- [Requirements checklist](#requirements-checklist)
- [Possible improvements](#possible-improvements)

---

## Quick start

**Prerequisites:** **JDK 21+** and **Maven 3.9+** (for the backend); **Node.js ≥ 20** (for the
frontend). The backend targets Java 25 bytecode but runs on any JDK ≥ 21.

```bash
npm install          # installs the frontend + shared-types workspaces
npm run dev          # starts the Java backend (:3001) and the Vue frontend (:5173) together
```

`npm run dev` uses `concurrently` to run `mvn spring-boot:run` and Vite side by side. Then open
**http://localhost:5173**, click **Start game**, and hit **Auto-play**. Watch the bot climb past
1000, or take over manually by clicking quests and shop items.

Run the pieces separately if you prefer:

```bash
npm run dev:backend   # mvn spring-boot:run   (http://localhost:3001)
npm run dev:frontend  # vite                  (http://localhost:5173, proxies /api → :3001)
```

The backend's API is documented with Swagger UI at **http://localhost:3001/swagger-ui.html**, and the
raw OpenAPI spec is at `/v3/api-docs`. Open it on the backend port directly, because Vite only
proxies `/api`. Every `POST` there plays a real turn against the live game.

Other scripts (from the repo root):

```bash
npm test              # frontend unit tests (Vitest)
npm run test:backend  # backend unit tests (JUnit via Maven)
npm run test:all      # both
npm run build         # build shared + frontend bundle + backend jar
npm run characterize  # re-run the live experiments (Java) → regenerate probabilities.json + report
```

---

## What it does

- **Live game board** — current lives, gold, level, turn, score (with a progress bar to 1000 and
  a "🎯 1000+ reached" milestone), and every quest with its risk tier, reward, and expiry,
  color-coded by danger.
- **Auto-play** — the bot plays turn-by-turn at an adjustable speed; **pause** and **step** give
  frame-by-frame control. It keeps going past 1000 to maximize score.
- **Manual play** — click any quest to solve it or any shop item to buy it. The move the bot
  *would* make is marked with a ★.
- **Reputation lookup** — a 🔍 button asks the game how the dragon stands with the people, the
  state and the underworld. It is a *move*, not a readout: the call spends a turn, so it is manual
  only, disabled during auto-play, and the HUD stamps each reading with the turn it was taken on
  and marks it stale once the game moves past it.
- **Decision log** — a running, human-readable explanation of every action and its outcome.
- **Encrypted quests** — decoded transparently (Base64 and ROT13) and marked with a 🔓.
- **Robust error handling** — upstream hiccups, dead games, and network failures surface as clear
  messages instead of crashes; auto-play pauses safely on error.

---

## Architecture

An npm-workspaces frontend (Vue + shared types) plus a standalone Maven backend:

```
         Vue 3 SPA (Vite)                 Java backend (Spring Boot)      Game API
   ┌───────────────────────┐        ┌──────────────────────────┐   ┌──────────────────┐
   │ Pinia store            │  HTTP  │ GameController → Service   │   │ dragonsofmugloar │
   │ TanStack Vue Query     │ ─────► │  ├─ GameApiClient (retry, │──►│  /api/v2/...     │
   │ Components + auto-play  │  /api  │  │   timeout, decoding)   │   └──────────────────┘
   │  (typed by src/types)   │ ◄───── │  ├─ GameStore (sessions)  │
   └───────────────────────┘        │  └─ StrategyEngine.decide()│
                                     └──────────────────────────┘
```

- **`packages/backend`** — a **Spring Boot 4 / Java 25** app (Maven). It owns all game logic:
  - `StrategyEngine.decide()` — the pure, exhaustively-tested solver.
  - `GameApiClient` — a typed `java.net.http` client with timeouts, retry-with-backoff, error
    mapping, and Base64/ROT13 decoding of encrypted ads.
  - `GameStore` — an in-memory session store. The upstream API has **no get-state endpoint** (state
    only comes back from start/solve/buy), so the backend is the source of truth for live state.
  - `GameService` + `GameController` — orchestration and a clean REST surface, with a
    `@RestControllerAdvice` mapping errors to a structured body.
  - A `characterization` tool that measures the game's hidden mechanics (see below).
- **`packages/frontend`** — the **Vue 3** SPA (`<script setup>` SFCs). **Pinia** holds durable
  game/log state; **TanStack Vue Query** manages the server calls; a `useAutoPlay` composable runs
  the auto-play loop.
  The frontend's `src/types` holds a small set of **TypeScript types** describing the backend's
  JSON API, so the SPA is fully typed against the contract. (All game *logic* lives in Java.)

**Why a backend at all?** The game API is CORS-open, so a pure SPA is possible — but the role is
fullstack, and a backend is the right home for the strategy engine, session state, retry/error
handling, and decoding. It's also the natural seam for the wider team stack (Redis, RabbitMQ).

---

## The solver strategy

The game ends only when **lives reach 0** — there is no built-in victory — so the core challenge is
*survival*. **1000 is a requirement, not a finish line:** the bot plays *past* it and stops only
when it dies (or you pause). `StrategyEngine.decide()` is a pure function returning the single best
next action:

1. **Survive first** — buy a Healing Potion when lives are low and it's affordable.
2. **Only ever attempt a safe quest** — a quest below `safeProbability` is off the table, whatever
   it pays and however many lives are in hand. The floor is unconditional, and it is applied to the
   *measured* probability: a quest priced above its tier's reward ceiling is valued at the rate
   measured there, so an expensive "Sure thing" fails this floor like the coin flip it really is.
3. **Among the safe quests, take the cheapest** — the lowest reward, not the highest expected
   value. This was originally justified by cheap quests being plentiful; the characterization later
   supplied the stronger reason, which is that within one tier a dearer quest genuinely fails more
   often. The policy turned out to be right for a better reason than it was chosen for — and it is
   also why the reward ceiling barely changes anything in practice.
4. **Take the safest quest when the board is bad** — when nothing clears the floor, play the best
   odds, ties going to the **cheaper** quest. Below the floor every option costs a life at some
   rate, so odds are the only thing left worth optimising; expected value would trade survival for
   a purse the dragon probably won't collect. (An earlier version broke ties toward the bigger
   purse, assuming equal labels meant equal risk. The measurements say they do not.) Buying a
   re-roll was the earlier answer, but gold is for staying alive: a bad board costs a risk, not the
   potion fund — and the board offers a safe quest on ~96% of turns, so this path is rare.
5. **Upgrade only out of surplus** — one trigger: gold above `goldDumpThreshold`, and the purchase
   must still leave `upgradeGoldReserve` behind, so there is always potion money. The cheapest
   upgrade is 100 against a 50-gold potion, so buying one on impulse costs two lives the dragon
   then cannot buy. With the shipped 300/400 the first upgrade lands just past 400 gold.

The only stop condition is `lives == 0`. Per-tier success probabilities are **not hardcoded** — they
are loaded from `probabilities.json` on the classpath, produced by the characterization tool, so
every constant is traceable to a measurement. Where a measurement contradicted an assumption, the
assumption lost: see [what didn't pan out](#-tried-and-it-didnt-pan-out).

---

## Characterization: measuring the hidden mechanics

The API documents endpoints but **not effects**: the shop returns only `{id, name, cost}`, and quest
success is probabilistic behind a text tier. Rather than guess, a Java tool
(`packages/characterization`) measures the game against the live API and writes both the solver's
data file (`probabilities.json`) and a report per experiment. Roughly 5,500 live attempts across
five runs back the sections below.

Everything here is reported as **settled**, **tried and didn't pan out**, or **still open**. The
negative results are kept deliberately — they are the expensive part, and without them the next
person re-runs the same dead ends.

### ✅ Settled — measured, and acted on

- **The shop is fixed.** Every fresh game returns an identical layout and prices (`hpot` 50; five
  upgrades at 100; five at 300). No per-game randomization, so the run that proves it is split out
  and no longer repeated.
- **Encrypted quests use two ciphers:** `encrypted: 1` is **Base64**, `encrypted: 2` is **ROT13**,
  validated against captured live samples. A Base64-only decoder silently corrupts every type-2
  quest.
- **Tier names are only roughly ordered.** "Walk in the park" (0.92) beats "Piece of cake" (0.89),
  and "Rather detrimental" (0.42) beats "Risky" (0.41). The solver trusts measured probabilities,
  never the label's rank.
- **A tier label stops predicting above a price — the biggest finding here.** Pooled over all three
  attempt logs (3,843 attempts), a "Sure thing" is **307/309 below 130 gold** and **13/149 at or
  above it** — 99% against 9%. "Piece of cake" breaks at 150 (96% → 46%). "Walk in the park" and
  "Quite likely" were checked and are **flat**, so they get no ceiling. The cliff replicates
  independently in all three runs, which used three different quest pickers.
  The turn counter is a passenger, not a cause: hold reward under 50 and a "Sure thing" is 100% at
  every stage of the game. Rewards simply climb as the game runs, which is why late-game quests
  *look* harder.

- **Investigating reputation costs a turn.** `POST /{gameId}/investigate/reputation` returns only
  `{people, state, underworld}` — no lives, gold or turn — but it advances the game exactly as a
  solve does: three consecutive calls between two solves moved the counter from 1 to 5. Nothing in
  the response says so, so `GameState.afterInvestigate()` applies the `+1` locally or the HUD
  silently drifts a turn behind the real game. This is why the SPA exposes it as a deliberate
  button rather than a live tile, and why the solver never calls it.
- **Reputation is fractional, and moves slowly.** `people` climbs by about **0.1 per solved quest**
  — `0.1, 0.2, 0.30000000000000004, …` — so the figures are doubles rounded to two decimals. Typed
  as `int` they read 0 for the first half-dozen solves of a game and then sat a whole point low,
  since Jackson truncates toward zero rather than rounding. `state` and `underworld` barely move at
  all: across three games `underworld` never left 0, and `state` held 0 for ten solves before
  dropping to −2 and staying there.

### ❌ Tried, and it didn't pan out

- **Skipping about-to-expire quests: no benefit.** A quest on its final turn succeeds as often as a
  fresh one of the same tier — 54% vs 59% over 699 paired attempts drawn from the same boards, 95%
  CI [-12.7, +2.0]pp, odds ratio 0.88. A rival implementation refuses quests with fewer than two
  turns left; on this evidence that discards cheap safe work and buys nothing. **Not adopted.**
- **Pricing quests by their reward ceiling does not raise the score.** The cliff above is real, but
  A/B-ing it over 40 games changed nothing measurable: median 2,852 with ceilings against 3,038
  without (p = 0.76), and both reached 1000 in 19 of 20 games. Replaying every recorded board
  through both valuations shows why — the ceiling changes the solver's pick on **0.9% of boards**,
  because the solver already takes the *cheapest* quest clearing its safety floor and so was never
  choosing expensive ones. The valuation is kept for honest odds in the UI, **not** as a scoring
  win.
- **Asking "does the dearer of two quests fail more?" found nothing — the question was the wrong
  shape.** An earlier experiment paired the cheapest and dearest quest of one tier and compared
  them: 72.1% vs 74.6%, p = 0.32. But the effect is an *absolute threshold*, not a gradient, and
  both arms straddled it — within that same data, the dear arm is 28/28 below 130 gold and 3/16
  above, the cheap arm 27/27 and 3/13. A paired relative design cannot detect an absolute cliff.
  Worth remembering before designing the next contrast.
- **Rationing upgrades with an earned credit.** Upgrades once had two triggers: surplus gold, or one
  purchase banked per life lost. Moving the gold trigger to 400 left the credit deciding anything
  only at *exactly* 400 gold, so it was deleted rather than kept as near-dead state.

### ❓ Still open to interpretation

- **Does levelling actually help?** The assigned-arm experiment (never-upgrade vs always-upgrade)
  came back mostly within noise, and its one big gap — "Sure thing" 100% vs 81% — is confounded by
  the reward cliff, since the arms do not meet the same quests. Unresolved; the solver treats
  upgrades as surplus spending rather than an odds improvement.
- **Is there any real difficulty scaling over time?** Holding level fixed, "Sure thing" falls from
  100% to 22% across turn buckets — but rewards climb over the same span, and the cliff explains
  that on its own. The two have not been separated cleanly.
- **Does reputation affect anything?** The SPA can now look it up on demand, but no strategy reads
  it — neither this solver nor the rival implementation we compared against. Since each look costs
  a turn, measuring it means paying for the sample, and whether the three figures move quest
  outcomes at all is still entirely unmeasured. The button exists so a human can look, not so the
  bot can act.
- **The tuning constants have never been A/B-tested.** `safeProbability 0.7`,
  `goldDumpThreshold 400` and `upgradeGoldReserve 300` were reasoned about, not measured. The
  benchmark harness now exists to settle them.
- **Most games still end in death before turn 100** (16 of 20 in one arm, 12 in the other). Whether
  a more conservative solver would trade points for survival, and whether that is even desirable
  when the goal is score, is untested.

### How the solver actually performs

`npm run benchmark` is the only run that drives `StrategyEngine.decide()` and obeys it, so it is the
only one whose scores describe the solver rather than the game:

| Games | Median score | Points/turn | Reached 1000 |
| ---: | ---: | ---: | ---: |
| 40 (100-turn cap) | ~2,900 | ~31 | **38/40** |

### Reproducing

```bash
CHAR_GAMES=40 npm run characterize   # P(success | tier) — regenerates probabilities.json
npm run characterize:level           # does levelling improve the odds, or does difficulty scale?
npm run characterize:reward          # is difficulty tied to prize money?
npm run characterize:expiry          # do near-expiry quests beat their label?
npm run benchmark                    # score run: A/B the solver itself
```

Each run writes a CSV of raw rows next to its report. **Re-analyse from the CSV rather than
re-running** — the reward cliff was found by re-reading logs that had already been collected for a
different question.

---

## Testing

**92 tests**, covering business logic only — deliberately not controllers, transport or presentation.

- **Backend (JUnit 5) — 49.** `StrategyEngineTest` (26: the safety floor, cheapest-safe selection,
  reward ceilings, the forced-gamble fallback, upgrade triggers, play-past-1000),
  `ProbabilitiesTest` (4), `GameServiceTest` (11: state sync from solve/buy, game-over
  short-circuit, and the turn a reputation lookup spends), `MessageDecoderTest` (8: both ciphers).
- **Characterization (JUnit 5) — 32.** The arithmetic that interprets an experiment, kept pure and
  API-free: `AttemptAnalysisTest` (12), `ExpiryPairingTest` (11), `ExpiryReportTest` (4),
  `ScoreSummaryTest` (4), `ReportWriterCeilingTest` (1). A live run costs the better part of an
  hour, so a table computed wrongly would cost a whole re-run to discover.
- **Frontend (Vitest + Vue Test Utils) — 11.** Pinia store transitions (milestone / game-over / log
  / reputation reading) and the HUD.

Two of these exist for a specific reason worth knowing: `ProbabilitiesTest` and
`ReportWriterCeilingTest` guard the reward ceilings in the shipped `probabilities.json`. Those
numbers are derived offline and only *carried forward* by `npm run characterize`, so if a
regeneration ever dropped them, nothing else in the suite would notice — the solver would silently
go back to trusting an expensive "Sure thing".

**Not covered, so change with care:** `GameApiClient` retry/backoff, the controller validation → 400
path, `GameStore`, the Vue components beyond the HUD, and the experiment run-and-render loops.
Verification for those is manual.

Beyond unit tests, the solver is verified end-to-end against the **live** API by `npm run
benchmark`, which plays whole games through `StrategyEngine.decide()`. Most recent run: **38 of 40
games past 1000**, median ~2,900.

---

## Project structure

```
packages/
  backend/           # Java 25 + Spring Boot (Maven): domain, decoding, probabilities,
                     #   StrategyEngine, GameApiClient, GameStore, GameService, REST, + JUnit tests
                     #   └─ com.mugloar.characterization: the live-measurement tool
  frontend/          # Vue 3 + Vite SPA: Pinia stores, composables, components (+ Vitest tests)
                     #   └─ src/types: TypeScript types describing the backend's JSON API
```

---

## Tech choices

| Concern | Choice | Why |
| --- | --- | --- |
| Backend | Java 25 + Spring Boot 4 (Maven) | Team stack; the natural home for the solver + state |
| Frontend | Vue 3 + Vite | Team stack; `<script setup>` SFCs |
| Client state | Pinia | Idiomatic Vue store for game/log state |
| Server state | TanStack Vue Query | Purpose-built for async calls, in-flight & error state |
| API types | TypeScript types in `frontend/src/types` | Frontend typed against the backend contract |
| Backend tests | JUnit 5 + Mockito + MockMvc-style integration | Standard, thorough |
| Frontend tests | Vitest + Vue Test Utils | Fast, one runner |

---

## Requirements checklist

| Requirement | Where |
| --- | --- |
| Web app on the provided game | Vue 3 SPA + Java Spring Boot backend |
| Responsive & cross-browser | Scoped CSS, fluid grid, standard APIs (fetch, `AbortController`) |
| State management | Pinia (client) + TanStack Vue Query (server state) |
| Reaches 1000+ points | Java `StrategyEngine.decide()`; 38/40 games past 1000, median ~2,900, measured live; plays past 1000 |
| Error handling & input validation | Retry/backoff + timeouts + error mapping; id validation on every route |
| Unit tests | JUnit (backend 46 + characterization 32) + Vitest (frontend 12) |
| Documentation | This README + a generated characterization report |

---

## Possible improvements

- Stream turns over WebSocket/SSE instead of the frontend polling `auto-step`.
- Persist sessions in **Redis** and evict finished games from the in-memory store.
- Publish turn events over **RabbitMQ** for an analytics/replay consumer.
- Settle whether `level` measurably shifts odds — the assigned-arm sample came back inconclusive and
  its one large gap is confounded by the reward cliff, so upgrades are currently treated as surplus
  spending rather than an odds improvement.
- A/B the tuning constants (`safeProbability`, `goldDumpThreshold`, `upgradeGoldReserve`) with
  `npm run benchmark`. They were reasoned about, never measured; the harness now exists.
- Measure whether reputation affects outcomes at all. The SPA can read it, but no strategy does —
  and since each look costs a turn, a characterization arm would have to budget for that.
- Add end-to-end (Playwright) tests driving the real UI.
