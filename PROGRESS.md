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

## The settlement, corrected (2026-08-27)

**Status: EVERY PRINTED ROW NOW PRICES EXACTLY**

The players sent a correction, unprompted: *"Lo de cobrar no está bien. Ahora te lo pongo
como es."* It is recorded verbatim, translated, modelled and checked in
**`payment-rules.md`**, which is now the authority on what a hand pays.

Three things changed.

**The estutxe is a stake, not a fee.** It was collected *for going* - by both of the going
pair, win or lose. It is signed by the result: +1 each on a win, -1 each on a loss, exactly
like primeres. It stays the side's rather than a player's. This is the one that matters:
the book's *"Si perden i tenen l'estutxe, 3 cadegú"* used to come out at 1, and now comes
out at 3. `SettlementCalculatorTest.TheBookTables` prices all sixteen reachable rows of
both of the book's tables, and they all read as printed.

**A hand runs until a side has five.** It used to end the moment the opposing side had
four, on the reasoning that five was then out of reach. It is out of reach, and the hand is
played on anyway - so eight basas can finish **4-4**, which is the going side falling short
like any other failure. `Round.BASAS_TO_BLOCK` is gone.

**Your own king falling is a question.** The engine used to decide between "you put your own
king" (±4) and "the king fell, hand over" (-1) by asking whether the player had a legal
alternative. They are two answers to the same question, and the player picks:
`WAITING_FOR_KING_CHOICE`, `KingChoiceUseCase`, a pair of buttons, and a bot that always
takes the certain 1. Only a *forced* king asks - putting your own king down when you had
another card commits you. And a king forced out of anyone else now costs them nothing:
they are the helper, willing or not.

| Piece | Where |
|-------|-------|
| The correction, and what it is checked against | `payment-rules.md` |
| The model | `SettlementCalculator` |
| Every row of the book, priced | `SettlementCalculatorTest.TheBookTables` |
| Five decides it, either way | `Round.checkForRoundEnd` |
| The king question | `Round.withKingChoice`, `KingChoiceUseCase` |

One thing is left open, and it is the only place the book and the players cannot both be
right: the book's extra coin for holding the going side under four basas. Implemented as a
flat 1, with the reasoning in `payment-rules.md` §6.

**The settlement says what it charged.** `Settlement` is a list of lines per player rather
than a net figure - the base, primeres, the estutxe, a dengue, whatever moved - and
`playerDeltas()` is their sum, so the itemisation cannot drift from the total. The lines
ride along on `RoundEnded` and the client lays them out when a round settles. Half a dozen
things can move a player's coins in one hand, and before this the table only ever saw the
total.

---

## Bots (not a design phase)
**Status: PLAYABLE, DELIBERATELY STUPID**

Five players is a lot to find. Any empty seat can be taken by a bot, so one person can
sit down and play a hand.

| Piece | Where |
|-------|-------|
| What a seat has to decide | `game/domain/service/BotStrategy` |
| The one that exists: a legal card at random | `game/domain/service/RandomBotStrategy` |
| Which seats have nobody behind them | `game/application/BotRoster` |
| Taking their turns | `game/application/BotTurnDriver` |
| Every legal card in a hand, at once | `MoveValidator.legalCards` |
| Seating them | `POST /api/rooms/{id}/bots`, `FillWithBotsUseCase` |
| Making them up | `lobby/adapter/out/BotSeatsAdapter` (the `BotSeats` port) |

**A bot is an ordinary player.** It has a player id, sits in a room, is dealt a hand, and
wins and loses coins against the posso like anyone. The only thing that marks it out is
being in the roster, which is what tells the driver its turn will not take itself. Its
moves go through the same use cases a person's client does, so it cannot cheat: the same
`MoveValidator` judges its card, and a bug in a strategy shows up as a *rejected* move
rather than an illegal one. `BotTurnDriverTest` pins that with a strategy that tries.

**How a turn gets taken.** Nothing in the game moves itself, so every entry point that
changes a game calls `BotTurnDriver.drive` afterwards - the five WebSocket handlers, and
`GameStarterAdapter` when the game is dealt, since the Soledad window is open from the
start. The driver plays out every bot decision standing between there and the next thing
a person has to do, then stops.

Moves are spaced (`ginebra.bots.move-delay`, 800ms) and run off the caller's thread.
Instant bots would resolve a whole basa inside the human's own click, so nobody would see
the cards land.

**What "dumb" means here, exactly.** Among the cards the rules allow it picks uniformly,
and it names a trump at random. But it declines both *wagers* - Soledad and "fer todo" -
outright. That is not laziness: a random Soledad would make one hand in two someone going
alone on nothing, and a play-test would never see a normal round. The split between a
move and a wager is the shape of `BotStrategy`, and the wagers are the first thing a
better opponent should take over.

**Not done:** anything that looks at the cards. No count of trumps, no memory of what has
been played, no notion of a partner - a bot will happily trump its own side's winning
basa. `BotStrategy` is the seam; `RandomBotStrategy` is one implementation of it.

---

## Play Client (not a design phase)
**Status: MINIMAL, PLAYABLE**

A single static page served by Spring at `/`, added so the game can be played by hand
without a frontend project. No build step, no npm, no CDN: `app.js` speaks STOMP over a
native WebSocket directly, because `/ws/game` is registered without SockJS.

| Piece | Where |
|-------|-------|
| Page, styles, icon | `src/main/resources/static/` |
| Name box before the lobby, remembered in `localStorage` | `#identity-gate`, `enterWithName` |
| What the round cost, line by line, when it settles | `#settlement`, `showSettlement` |
| "Play against bots", and filling a half-empty room | `#play-bots`, `#fill-bots` |
| Card faces drawn as a baraja española | `cardFace` / `spriteMarkup` in `app.js` |
| Help panel: full card order per trump, plus the rules in one screen | `? Help` in the header |
| Teams panel: both sides, who goes, basas each, live team totals | appears when the first King reveals them |
| Endpoints it needs are pinned by tests | `lobby/adapter/in/PlayClientEndpointsIntegrationTest` |

See `RUNNING.md` for how to start it, expose it for a remote play-test, and deploy it -
there is a `Dockerfile` and a `fly.toml`, and the app reads `$PORT` so any container
platform will take it. One instance only: games are in memory until Phase 5.
Open one browser tab per player. Each is asked for a name before the lobby appears, which
is what the other four see on the seat; `?name=Ada` in the URL fills it in and skips the
box, which is what makes five tabs on one machine practical. One tab creates a room, the
other four join from the list, and the game starts when the fifth is in. Verified by
driving five real Chromium tabs through a full basa: deal, Soledad window, trump, five
cards, basa resolution, and an out-of-turn rejection reaching only the offending tab.

`SecurityConfig` now permits `GET /`, `/index.html`, `/app.js`, `/style.css` and the
favicons. Everything else is unchanged - every API call the page makes still carries a JWT.

**A state snapshot can arrive after the events that overtook it.** `GAME_STATE` is built
when the server handles the subscription and goes to the player's private queue, while
play events go to the game topic; nothing orders those two against each other. Applying a
snapshot that has been overtaken rewinds the table, puts a played card back in the hand,
and hands the turn back to someone who has already had it - and because the first card on
the table is what decides which cards the page offers, the page ends up offering a card
the server refuses (`MUST_PLAY_TRUMP` on a table whose real lead was something else).

Bots made it visible: before them a client only ever re-subscribed at a moment when the
table was idle, and now there is always something in flight.

The client keeps its own position - round, then basa, then cards on the table - and
ignores any snapshot behind it. `CardPlayed` now carries its `basaNumber` too, so a table
left over from an earlier basa is cleared rather than built on, which also covers a
`BasaWon` that never arrives.

**Every pause announces itself.** A round that stops and says nothing is a round nobody
can play on: the table sees a hand in progress, no turn, and no way to answer. Two bugs
have had that shape - going alone opened a window the client was never shown, and a clean
sweep to five paused the hand in silence, reported twice from a real table as *"cuando yo
hago la quinta basa, el juego para i no puedo seguir"*. `GameServiceTest.PausesAreAnnounced`
now pins it for every waiting status at once rather than one at a time.

Two things the client works around rather than fixes:

- **No state-refresh message.** The server only pushes `GAME_STATE` on SUBSCRIBE, so after
  a round ends (the next round is dealt server-side) the client re-subscribes to the game
  topic to get its new hand. A `REQUEST_STATE` client message would be cleaner.
- **No lobby push channel.** Players who joined before the room filled poll
  `GET /api/rooms/{id}` once a second until it reports a game id.
- **No acknowledgement that a subscription is live.** This also makes
  `GameWebSocketIntegrationTest.shouldReceiveGameStateOnSubscribe` occasionally flaky - it
  can see `PLAYER_CONNECTED` before `GAME_STATE`. A `REQUEST_STATE` message would fix the
  test and the workaround together. The server pushes `GAME_STATE` when
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

### The cards

The faces are drawn in SVG rather than fetched, so there is still nothing to install and
no licence to honour. The four suits and the three figures are symbols in a hidden sprite,
instantiated with `<use>`; a card is a frame, its pips and two indices. What makes them
read as a *baraja española* rather than as generic cards is the numbering (1-7, 10, 11,
12 - no 8 or 9), the pip layouts (2-3-2 for a seven), and the **pinta**: the break in the
border that names the suit from a card only half out of the fan - none for Oros, one for
Copas, two for Espadas, three for Bastos.

Card names in the log and in tooltips are the Spanish ones (*Rey de Bastos*), and a
tooltip adds the village name when a card has one - Espadilla, Manilla, Basto, Rovell,
Carabassa - which depends on the trump in play.

Not attempted: the React + TypeScript + Vite stack in spec §3.2, reconnection UI, a
control for the *"es primer rei aida"* call. The page shows the posso, the round's mode
and both sides.

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
- **Five basas decides it, for either side**, so eight can finish 4-4 with the going side
  falling short (corrected by the players 2026-08-27 - see the section above)
- The first King decides the round's shape - **helped**, **posar-se el rei**, or, when the
  one who goes has their own king forced out, **their choice** of carrying on alone or
  stopping for 1 (`RoundMode`, `Round.withKingPlayed`, `Round.withKingChoice`). A king
  forced out of anyone else costs them nothing. `TeamResolver` is gone; its job moved into
  the aggregate
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
`SpecCardOrderTest.shouldRankEveryColumnAsThePlayersStatedIt`. The four-kings choice was confirmed the same day: the holder may take the 4 or play the
hand out alone, keeping the 4 either way. The fallen king and the change-of-suit rule were confirmed on 2026-08-26 too: the mà pays 1
and the cards are dealt again with nobody else charged, and the leader must open with a suit
not yet led that round rather than merely a different one. The todo penalty and the estutxe were priced on 2026-08-26 too: a missed todo costs 1, so
the call is now an explicit decision (`WAITING_FOR_TODO`, `TodoUseCase`, a client button);
and the estutxe pays every player on the going side, win or lose, which makes the source's
printed rows come out exactly. One printed row is still unexplained and is written up in
`rules-questions.md`.

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
