# What is left to do

Ordered by what would actually go wrong, soonest. Everything here was checked against the
code on 2026-08-28, not read off an older note — `PROGRESS.md` described the play client as
"playable" while going alone was quietly broken, so this list only claims what was verified.

**Where things stand.** Phases 1–4 are complete: identity, lobby, the game engine and the
WebSocket layer. The rules are reconciled against the book and against the players, and
every printed row of both pay tables prices exactly. 847 tests pass. Bots fill the empty
seats. Phases 5 and 6 have not been started at all.

---

## 1. Before the next play-test

### 1.1 Timeouts — nothing anywhere ever fires
**Size:** a day. **Where:** new scheduler; `Round.soledadDeadline`, `ConnectionTracker`.

There is no `@EnableScheduling` or `@Scheduled` in the codebase, so every timeout in
design §8 is missing. One person closing a tab mid-hand stalls that table **permanently**:
nothing auto-passes, nothing substitutes, nothing cleans up. This is the single most likely
way a real session ends badly.

- **Soledad auto-pass after 2 minutes.** `soledadDeadline` is already computed and stored
  on the round; nothing reads it. Needs a `SoledadAutoPassed` event so the table can see
  why the window closed.
- **Disconnect handling.** Disconnects *are* detected — `WebSocketEventListener` publishes
  `PlayerDisconnected` — but nothing acts on it beyond a line in the log. Design calls for
  a 5-minute pause, then something decisive.
- **Room expiry after 30 minutes**, so abandoned rooms stop cluttering the lobby.

**Done when:** a table with a player who has walked away resolves itself, and the others
can finish or abandon the hand without restarting the server.

### 1.2 A client smoke suite
**Size:** an hour or two. **Where:** new `e2e/`, Playwright, already installed.

The client has **no automated tests at all**, and that gap just cost a bug: going alone
was broken while all 847 server tests stayed green, because the fault was in client state
the Java suite cannot see. The scripts written while debugging it are most of the work
already.

Cover: the four decision windows (soledad, trump, king, todo) opening for the right player
and disabling for everyone else; a round boundary delivering the new hand; and the sheet's
lines adding up to its totals.

**Done when:** `npm test` (or a gradle task) fails if a decision window stops opening.

---

## 2. Before it is more than a play-test

### 2.1 Persistence (Phase 5)
**Size:** several days. **Where:** nothing exists yet — no JDBC or Testcontainers in
`build.gradle.kts`.

Every table lives in memory. A redeploy kills every game in progress, so you cannot ship a
fix while anyone is playing, and one instance is the hard ceiling. Schema, repositories,
restart recovery.

### 2.2 Reconnection
**Size:** follows from 2.1.

Closing a tab drops that player and the round cannot continue. Needs 2.1 plus a client
that can rejoin a game in progress.

### 2.3 A `REQUEST_STATE` message
**Size:** an afternoon. **Where:** `GameWebSocketController`, `GameSubscriptionInterceptor`.

The server only pushes `GAME_STATE` on SUBSCRIBE, so the client re-subscribes to ask for
it — which is why state can arrive out of order, why the client carries a position guard,
and why `GameWebSocketIntegrationTest` is occasionally flaky. One message would retire all
three.

### 2.4 Rate limiting (Phase 6)
Anyone who finds the URL can create rooms without limit.

---

## 3. Bugs and small fixes

Each of these is real, small, and independent.

| | What | Where |
|---|---|---|
| 3.1 | `RoomService` mutates a shared `Room` with no lock, so two people joining at once can both see four seats free | `lobby/application/RoomService` |
| 3.2 | Shuffling uses `new Random()` — a 48-bit LCG — in a game played for coins. `SecureRandom` is a one-line change | `GameBeanConfig.gameRandom()` |
| 3.3 | `NoOpGameEventPublisher` relies on `@ConditionalOnMissingBean` on a scanned component. It resolves correctly today, but the ordering is not guaranteed outside auto-configuration | `game/adapter/out/` |
| 3.4 | A player can sit in several rooms at once; design §8 says one active game each | `lobby/application/RoomService` |

---

## 4. Rules still open

- **The book's extra coin for holding the going side under four basas.** The book pays each
  opponent 2 instead of 1; the players' 2026-08-27 correction says a flat 1, which is what
  is implemented. It is the one place the two sources cannot both be right —
  `payment-rules.md` §6 sets out the reasoning, and the question for the table is written
  out in `rules-questions.md`. Two lines to change if they say otherwise.
- **"Es primer rei aida"** exists in the domain (`Round.firstKingCalled`) and is carried in
  the state payload, but has no message and no control. Nobody can actually call it.

---

## 5. Product

- **The card faces.** Drawn in SVG, and they read well at hand size, but they are stylised
  rather than the Fournier engraving. Switching to images is an afternoon — everything is
  behind `cardFace()` in `cards.js` — but it needs an image set with a licence. Fournier's
  own artwork is copyrighted; public-domain scans exist on Wikimedia, which is unreachable
  from the build machine.
- **Better opponents.** `BotStrategy` is the seam and `RandomBotStrategy` is one
  implementation of it. The bot plays a legal card at random and declines every wager. The
  first real improvement is the wagers — soledad, todo, the king choice — because those are
  decisions with a right answer rather than a preference.
- **Registered accounts.** Only the anonymous path exists (spec 1.3).

---

## 6. Deliberately not doing

- The React + TypeScript + Vite stack in spec §3.2. The hand-rolled client is smaller than
  its build config would be, and has no build step at all.
- A `SoledadValidator` class (design §5.3). That logic is three lines in `Round` and
  `GameService` and does not want a home of its own.
