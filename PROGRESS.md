# Ginebra Online - Implementation Progress

This document tracks what has been implemented.

Last verified: 2026-08-25 — 696 tests, 11 skipped, 0 failures (`sh gradlew test`).

---

## Phase 1: Identity Foundation
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | POST /api/auth/anonymous endpoint | Done |
| 2 | GET /api/auth/me endpoint | Done |
| 3 | JWT validation filter | Done |
| 4 | In-memory token store (for anonymous session tracking) | Done |

Not in scope of this phase, still outstanding: registered user accounts (spec 1.3),
anonymous identity cleanup after 24h of inactivity (design §8).

---

## Phase 2: Lobby - Room Management
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | Room domain model | Done |
| 2 | POST /api/rooms (create room) | Done |
| 3 | GET /api/rooms (list joinable rooms) | Done |
| 4 | POST /api/rooms/{id}/join | Done |
| 5 | POST /api/rooms/{id}/leave | Done |
| - | GET /api/rooms/{id} (single room lookup) | Done |
| - | In-memory room repository | Done |
| - | Room lifecycle (WAITING → STARTING) | Done |

`GET /api/rooms/{id}` was added for the play client: joining only returns a game id to the
fifth player, so the other four had no way to learn that the room had become a game. It is
restricted to members of the room.

Outstanding: 30-minute room expiry (design §8), one-active-game-per-player (design §8),
and `RoomService` has no per-room locking, unlike `GameService`.

---

## Phase 3: Game Engine - Core Domain
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | Card, Suit, Rank (value objects) | Done |
| 2 | Deck | Done |
| 3 | CardRankingService (all 4 trump scenarios) | Done |
| 4 | Basa, PlayedCard | Done |
| 5 | Round | Done |
| 6 | Game (aggregate root) | Done |
| 7 | MoveValidator, BasaResolver, TeamResolver | Done |

Card ranking is pinned to all four trump tables of spec 2.4 by `SpecCardOrderTest`,
including the Manilla switch (7 for Copas/Oros, 2 for Espadas/Bastos).

**Rule correction (2026-08-25), card order:** the low cards run in opposite directions
depending on the suit, trump or not — **Copas and Oros** rank 2 > 3 > 4 > 5 > 6 > 7, while
**Espadas and Bastos** rank 7 > 6 > 5 > 4 > 3 > 2. `CardRankingService` applied the
Espadas/Bastos direction to every suit, so a non-trump Copas or Oros basa could be awarded
to the wrong player. `spec.md`'s four tables had it right all along; its "Key Principles"
summary line contradicted them and has been corrected. `SpecCardOrderTest` now transcribes
all four tables and checks every column in both directions, so the two cannot drift again.
The tables also listed the Manilla twice — at position 2 and again at the foot of the trump
column — against the spec's own "Note on Manilla"; those four rows are gone.

**Rule correction (2026-08-25), basa lead:** the winner of a basa leads the next one. `spec.md` 2.3
previously said the opposite — "the player to the right of the previous starting player
(NOT the basa winner)" — and `Round.getNextBasaStarter` implemented that faithfully. The
spec line was the error; spec, the design worked example, the domain and its tests are now
aligned on winner-leads. Rotation to the right still applies **between rounds**
(`Game.startNextRound`, spec 2.2), which is a separate rule and unchanged.

Soledad is now complete — see "Rules & Known Gaps" below.

---

## Phase 4: WebSocket Integration
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| - | WebSocket configuration with STOMP | Done |
| - | /ws/game endpoint | Done |
| - | Message types (client→server, server→client) | Done |
| - | Connection tracking | Done |
| - | Game event broadcasting | Done |
| - | Room → Game transition trigger | Done |
| - | Multi-client end-to-end test (exit criterion) | Done |

**Exit criterion — "5 clients can connect, play cards, see real-time updates" —
is covered by `FivePlayerGameE2ETest`**: five real STOMP clients authenticate over REST,
fill a room, connect, receive private hands, pass Soledad, select trump, play a complete
basa and observe every broadcast. Previously the only WebSocket test asserted that a
single subscriber received `GAME_STATE`, which did not demonstrate the criterion.

Known weakness in this phase: `GameSubscriptionInterceptor` checks game membership from an
`@EventListener` on `SessionSubscribeEvent`, which fires *after* the broker registers the
subscription — so returning early does not veto it. A non-member who knows a gameId can
subscribe to `/topic/game/{id}` and follow the broadcast stream. Private hands are not
exposed (they go over `/user/**`), so this is unauthorised spectating, not cheating.

---

## Play Client (not a design phase)
**Status: MINIMAL, PLAYABLE**

A single static page served by Spring at `/`, added so the game can be played by hand
without a frontend project. No build step, no npm, no CDN: `app.js` speaks STOMP over a
native WebSocket directly, because `/ws/game` is registered without SockJS.

| Piece | Where |
|-------|-------|
| Page, styles, icon | `src/main/resources/static/` |
| Help panel: full card order per trump, plus the rules in one screen | `? Help` in the header |
| Teams panel: both sides, who goes, basas each, live team totals | appears when the first King reveals them |
| Endpoints it needs are pinned by tests | `lobby/adapter/in/PlayClientEndpointsIntegrationTest` |

See `RUNNING.md` for how to start it and how to expose it for a remote play-test.
Open one browser tab per player, `?name=Ada` to label a tab. One tab creates a room, the
other four join from the list, and the game starts when the fifth is in. Verified by
driving five real Chromium tabs through a full basa: deal, Soledad window, trump, five
cards, basa resolution, and an out-of-turn rejection reaching only the offending tab.

`SecurityConfig` now permits `GET /`, `/index.html`, `/app.js`, `/style.css` and the
favicons. Everything else is unchanged - every API call the page makes still carries a JWT.

Two things the client works around rather than fixes:

- **No state-refresh message.** The server only pushes `GAME_STATE` on SUBSCRIBE, so after
  a round ends (the next round is dealt server-side) the client re-subscribes to the game
  topic to get its new hand. A `REQUEST_STATE` client message would be cleaner.
- **No lobby push channel.** Players who joined before the room filled poll
  `GET /api/rooms/{id}` once a second until it reports a game id.
- **No acknowledgement that a subscription is live.** The server pushes `GAME_STATE` when
  the game topic subscription is registered, but STOMP frames are handled on a thread pool,
  so the topic SUBSCRIBE can be processed before the private queue SUBSCRIBE the push is
  addressed to - and the simple broker silently drops a message with no subscriber. It
  sends no `RECEIPT` to wait on either (verified against the running server), so the client
  re-asks until the state arrives. A `REQUEST_STATE` message would remove both this and the
  round-end refresh above.

The help panel derives the card order from the same rank tables as `CardRankingService`
rather than restating them, so it shows what the engine actually does.

The client mirrors `MoveValidator`'s follow-suit rule so it only offers legal cards; the
server remains authoritative and still rejects anything illegal.

Not attempted: the React + TypeScript + Vite stack in spec §3.2, reconnection UI, a
control for the *"es primer rei aida"* call, or any styling work beyond making the game
readable. The page shows the posso, the round's mode and both sides.

---

## Phase 5: Persistence & Recovery
**Status: NOT STARTED**

| Step | Description | Status |
|------|-------------|--------|
| - | PostgreSQL schema | Pending |
| - | JdbcTemplate repositories | Pending |
| - | Event logging | Pending |
| - | State snapshot updates | Pending |
| - | Server restart recovery | Pending |
| - | Player reconnection with state sync | Pending |

No persistence code exists and `build.gradle.kts` has no JDBC driver, PostgreSQL driver,
migration tool or Testcontainers dependency.

---

## Phase 6: Hardening
**Status: NOT STARTED**

| Step | Description | Status |
|------|-------------|--------|
| - | Rate limiting | Pending |
| - | Graceful error responses | Pending |
| - | Timeout handling | Pending |
| - | Game abandonment detection | Pending |

---

## Rules & Known Gaps

Section 1 records rules work that is now done. Everything after it is still implemented
partially or not at all despite living inside a phase marked COMPLETE.

### 1. Rules reconciled against the primary source — DONE

The photographed rules (Juan Monjo Soliveres, *«Es joc de ginebra»*) are transcribed and
translated in `rules-source.md`; `rules-diff.md` tracks every difference against `spec.md`.
The engine now implements the source's rules:

- Settlement runs through the **posso**, not player-to-player transfers, as one base plus
  +1 increments (`SettlementCalculator`)
- **No draw**: the going side needs 5 basas, the opposing side blocks with 4
- The first King decides the round's shape - **helped**, **posar-se el rei**, or the mà's
  king forced out ending the hand - and a forced king costs its owner 1 (`RoundMode`,
  `Round.withKingPlayed`). `TeamResolver` is gone; its job moved into the aggregate
- **Primeres**, **todo**, and the **four-king deal** are implemented
- A trump lead compels a trump unless a higher special card can be withheld
  (`MoveValidator`); the Espadilla and Basto no longer bypass following a plain suit

**Soledad (former Known Gap #1) is closed.** The declarer names trumps without becoming
the mà, the round stays 1-vs-4, it ends on the fifth basa or the opponents' fourth, it
settles at the Soledad rate, and rotation follows the normal starter. Every acceptance
test that was `@Disabled` for this now runs and passes.

Six readings the source leaves open are implemented one way and listed in `rules-diff.md`
§3.3 - most notably that **todo is auto-detected rather than called**, because there is no
timeout infrastructure to back a blocking decision window. D-17 (the leader must change suit until a King appears) is implemented too, and D-6 - where
the ace ranks in a non-trump suit - was confirmed by Tàrbena players on 2026-08-26: the
engine was already right, and the order they gave is now pinned by
`SpecCardOrderTest.shouldRankEveryColumnAsThePlayersStatedIt`. No rules question is
outstanding; the readings still worth confirming are listed in `rules-questions.md`.

Not wired to the client: the *"es primer rei aida"* call exists in the domain and the state
payload but has no control or message.

### 2. No scheduled work exists (all phases)

There is no `@EnableScheduling` or `@Scheduled` anywhere, so every resolved timeout in
design §8 is unimplemented:

- 2-minute Soledad auto-pass — `soledadDeadline` is computed and stored but nothing ever
  fires on it, and there is no `SoledadAutoPassed` event.
- 5-minute disconnect pause.
- 30-minute room expiry.
- 24-hour anonymous identity cleanup.

Design §5.3 also names a `SoledadValidator`; that logic currently sits inline in
`GameService` and `Round`.

### 3. Smaller items

- Registered user accounts (spec 1.3) — only the anonymous path exists.
- One active game per player (design §8) — not enforced; a player can sit in several rooms.
- `RoomService` mutates a shared `Room` with no lock, so concurrent joins can both see 4 players.
- Shuffling uses `new Random()` (a 48-bit LCG). Against the anti-cheat stance of spec 1.4,
  `SecureRandom` is the safer default for a coin game.
- `NoOpGameEventPublisher` relies on `@ConditionalOnMissingBean` on a scanned `@Component`,
  which is order-dependent outside auto-configuration. It resolves to
  `StompGameEventPublisher` today (verified at runtime), but the wiring is fragile.

---

## Build Environment

`build.gradle.kts` pins the Java toolchain to 17. A JDK 17 must be discoverable or the
build fails before compiling with *"Cannot find a Java installation ... languageVersion=17"*.
Containers and CI images that ship only a newer JDK need a toolchain download repository,
or the pin relaxed.

---

## Test Suite

| Area | Where |
|------|-------|
| Five clients over a real WebSocket (Phase 4 exit criterion) | `game/adapter/in/FivePlayerGameE2ETest` |
| Card order against all four spec 2.4 tables | `game/domain/service/SpecCardOrderTest` |
| Soledad rules, domain level | `game/domain/model/SoledadRoundRulesTest` |
| Soledad through the application service | `game/application/SoledadGameServiceIntegrationTest` |
| The settlement ladder, row by row | `game/domain/service/SettlementCalculatorTest` |
| The king rules and primeres/todo | `game/domain/model/KingRulesTest` |
| Following rules, including the trump-lead obligation | `game/domain/service/MoveValidatorTest` |
| Play client endpoints and public static assets | `lobby/adapter/in/PlayClientEndpointsIntegrationTest` |
| Single-room lookup | `lobby/application/GetRoomServiceTest` |
| Shared test doubles and fixtures | `support/` — `TestDeal`, `LegalMoves`, `LobbyFixture`, `StompTestClient`, `RecordingGameEventPublisher` |

No tests are disabled: 763 run, all green. The acceptance tests that were `@Disabled` for
the Soledad gap now pass and are enabled.

---

## Commit History

| Commit | Description |
|--------|-------------|
| eb3a95c | Add .worktrees/ to .gitignore |
| 71b81b7 | Phase 4 - WebSocket integration, game service, soledad, round/game domain updates |
| 967640c | intermediate state |
| 86df339 | Phase 3 - step 6, 7 - game aggregate root, MoveValidator, BasaResolver, TeamResolver |
| 4cbc225 | Phase 3 - step 5 Round - core domain |
| ac1767b | Phase 3 - step 4 game basa - core domain |
| 2cd22cb | Phase 3 - step 3 game engine - core domain |
| 4053933 | Phase 3 - Card, Suit, Rank |
| 0c59b45 | Phase 2 - steps 3,4,5 room endpoints |
| bd2a9ad | Phase 2 - /post for creating the room |
| 64ae76e | Phase 2 - Room domain model |
| 1b9d204 | Phase 1 - JWT filter |
| 86a64a4 | Phase 1 - /api/auth/me get anon user endpoint |
| 17e77ec | Phase 1 - /api/auth create anon session |
