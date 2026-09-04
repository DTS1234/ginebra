# Ginebra Online - Implementation Design v0.1

## Document Purpose

This document defines the architectural design for the Ginebra card game server. It prioritizes modularity, testability, and incremental delivery. Each phase produces a working, testable increment.

---

## 1. Quality Measures

These principles guide every design decision.

### 1.1 Modularity

The system is divided into bounded contexts, each deployable and testable in isolation. Contexts communicate through well-defined interfaces, never by reaching into each other's internals.

### 1.2 Separation of Concerns

Each component has a single reason to change:
- **Domain** - Changes only when game rules change
- **Application Services** - Changes only when use cases change
- **Adapters** - Changes only when external systems change (DB schema, WebSocket protocol, REST API shape)

### 1.3 Cohesion

Related concepts live together. A `Card` and its ranking logic belong in the same module. A `Room` and its join/leave logic belong together. High cohesion within modules, loose coupling between them.

### 1.4 Information Hiding & Abstraction

- Domain objects expose behavior, not data structures
- Ports define *what* is needed, not *how* it's provided
- Adapters can be swapped without domain changes
- Internal state is never leaked across context boundaries

### 1.5 Testability

Every component is testable at the appropriate level:
- **Domain logic** → Pure unit tests (no mocks, no I/O)
- **Application services** → In-memory adapters for fast tests
- **Adapters** → Integration tests with real infrastructure (Testcontainers)
- **End-to-end** → Full stack tests for critical paths

---

## 2. Bounded Contexts

Five contexts, each with clear responsibility boundaries.

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                 │
│                    (React Web, Future Mobile)                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          │ REST           │ WebSocket      │
          ▼                ▼                ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│    IDENTITY     │ │     LOBBY       │ │      GAME       │
│     CONTEXT     │ │    CONTEXT      │ │    CONTEXT      │
├─────────────────┤ ├─────────────────┤ ├─────────────────┤
│ • Anonymous ID  │ │ • Room CRUD     │ │ • Game Engine   │
│ • JWT validation│ │ • Join/Leave    │ │ • Rule validation│
│ • Player identity│ │ • Room listing │ │ • State machine │
└─────────────────┘ └────────┬────────┘ └────────┬────────┘
                             │                   │
                             └─────────┬─────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                                     ▼
          ┌─────────────────┐                   ┌─────────────────┐
          │   CONNECTION    │                   │   PERSISTENCE   │
          │    CONTEXT      │                   │    CONTEXT      │
          ├─────────────────┤                   ├─────────────────┤
          │ • WebSocket mgmt│                   │ • Game state DB │
          │ • Session tracking│                 │ • Recovery      │
          │ • Reconnection  │                   │ • Event log     │
          └─────────────────┘                   └─────────────────┘
```

### 2.1 Identity Context

**Responsibility**: Establish and verify player identity.

**Key Decisions**:
- Anonymous players get a server-generated UUID + JWT with `anonymous=true` claim
- Registered players use existing auth library (JWT with user details)
- Both token types are validated the same way downstream
- Anonymous tokens expire after 24 hours of inactivity
- Identity is just "who you are" - no game state here

**Boundaries**:
- IN: Auth requests, token validation requests
- OUT: Player identity (ID, display name, anonymous flag)

### 2.2 Lobby Context

**Responsibility**: Room lifecycle before game starts.

**Key Decisions**:
- Any authenticated player (including anonymous) can create a room
- Rooms are publicly listed until full (5 players)
- Room auto-transitions to GAME context when 5th player joins
- Room creator has no special privileges (no "host")
- If all players leave before game starts, room is deleted

**Boundaries**:
- IN: Create room, list rooms, join room, leave room
- OUT: Room state changes, game start trigger
- DEPENDS ON: Identity Context (player identification)

### 2.3 Game Context

**Responsibility**: All game rules and state management.

**Key Decisions**:
- Pure domain logic with no I/O dependencies
- Game state is the single source of truth
- All actions validated against current state
- State machine enforces valid transitions
- Produces events for every state change (for persistence and broadcast)

**Boundaries**:
- IN: Player actions (play card, select trump, declare soledad)
- OUT: Game events (card played, basa won, round ended, game ended)
- DEPENDS ON: Nothing (pure domain)

### 2.4 Connection Context

**Responsibility**: Real-time communication and session tracking.

**Key Decisions**:
- WebSocket per player per game
- Tracks connection state (connected, disconnected, reconnecting)
- Handles reconnection with full state sync
- Broadcasts game events to all players in a game
- Detects disconnection and notifies Game Context

**Boundaries**:
- IN: WebSocket connections, player messages
- OUT: Game state broadcasts, connection status changes
- DEPENDS ON: Identity Context (validate tokens), Game Context (forward actions)

### 2.5 Persistence Context

**Responsibility**: Durable storage and recovery.

**Key Decisions**:
- Persist game state on every card played
- Store as event log + current state snapshot
- Recovery loads snapshot, replays any missing events
- Active games loaded into memory on server start
- Completed games archived (retention policy TBD)

**Boundaries**:
- IN: Game events to persist, recovery requests
- OUT: Restored game state
- DEPENDS ON: Game Context (event definitions)

---

## 3. Hexagonal Architecture Per Context

Each context follows ports & adapters pattern.

```
┌─────────────────────────────────────────────────────────┐
│                      CONTEXT                            │
│  ┌───────────────────────────────────────────────────┐  │
│  │              DRIVING ADAPTERS                     │  │
│  │         (REST Controllers, WebSocket             │  │
│  │          Handlers, CLI, Tests)                   │  │
│  └──────────────────────┬────────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │              DRIVING PORTS                        │  │
│  │         (Use Case Interfaces)                    │  │
│  └──────────────────────┬────────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │              APPLICATION SERVICES                 │  │
│  │         (Orchestration, no business logic)       │  │
│  └──────────────────────┬────────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │                   DOMAIN                          │  │
│  │         (Entities, Value Objects, Rules)         │  │
│  │              Pure, No Dependencies               │  │
│  └──────────────────────┬────────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │              DRIVEN PORTS                         │  │
│  │         (Repository Interfaces,                  │  │
│  │          Event Publisher Interfaces)             │  │
│  └──────────────────────┬────────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │              DRIVEN ADAPTERS                      │  │
│  │         (PostgreSQL, In-Memory,                  │  │
│  │          WebSocket Publisher)                    │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Test Strategy Per Layer

| Layer | Test Type | Speed | Dependencies |
|-------|-----------|-------|--------------|
| Domain | Unit | < 1ms | None |
| Application Services | Unit with in-memory adapters | < 10ms | In-memory ports |
| Driving Adapters | Integration | < 100ms | Spring context, mocked services |
| Driven Adapters | Integration | < 1s | Testcontainers (Postgres) |
| Full Stack | E2E | < 5s | Full application + Testcontainers |

---

## 4. Endpoint Design

### 4.1 REST Endpoints (Lobby & Identity)

#### Identity

| Endpoint | Method | Purpose | Auth |
|----------|--------|---------|------|
| `/api/auth/anonymous` | POST | Create anonymous player session | None |
| `/api/auth/me` | GET | Get current player identity | JWT |

**POST /api/auth/anonymous**
- Request: `{ "displayName": "Player123" }` (optional)
- Response: `{ "token": "jwt...", "playerId": "uuid", "displayName": "Guest_a1b2" }`
- Generates UUID, issues JWT with `anonymous=true`
- If no displayName, generates one

**GET /api/auth/me**
- Request: JWT in Authorization header
- Response: `{ "playerId": "uuid", "displayName": "...", "anonymous": true|false }`

#### Lobby

| Endpoint | Method | Purpose | Auth |
|----------|--------|---------|------|
| `/api/rooms` | POST | Create a new room | JWT |
| `/api/rooms` | GET | List joinable rooms | JWT |
| `/api/rooms/{roomId}` | GET | Get room details | JWT |
| `/api/rooms/{roomId}/join` | POST | Join a room | JWT |
| `/api/rooms/{roomId}/leave` | POST | Leave a room | JWT |

**POST /api/rooms**
- Request: `{ }` (empty for now, future: options)
- Response: `{ "roomId": "uuid", "players": [...], "status": "WAITING" }`
- Creates room, adds creator as first player

**GET /api/rooms**
- Response: `{ "rooms": [{ "roomId": "...", "playerCount": 3, "createdAt": "..." }, ...] }`
- Only returns rooms with status=WAITING and playerCount < 5

**POST /api/rooms/{roomId}/join**
- Response: `{ "roomId": "...", "players": [...], "status": "WAITING" | "STARTING" }`
- If 5th player joins, status becomes STARTING, triggers game creation
- Returns error if room full or game already started

**POST /api/rooms/{roomId}/leave**
- Response: `{ "success": true }`
- Only valid before game starts
- If room becomes empty, room is deleted

### 4.2 WebSocket Endpoints (Game & Real-time)

#### Connection

| Endpoint | Purpose |
|----------|---------|
| `/ws/game/{gameId}` | Main game communication channel |

**Connection Flow**:
1. Client connects with JWT in query param: `/ws/game/{gameId}?token=jwt...`
2. Server validates JWT, identifies player
3. Server checks player belongs to this game
4. On success: sends CONNECTED + full game state
5. On reconnect: same flow, detects it's a reconnect, sends full state

#### Client → Server Messages

| Message Type | Payload | When Valid |
|--------------|---------|------------|
| `SOLEDAD_PASS` | `{ }` | Any player, during Soledad window |
| `DECLARE_SOLEDAD` | `{ }` | Any player, during Soledad window |
| `SELECT_TRUMP` | `{ "suit": "COPAS" }` | Player who "goes", after Soledad window closes |
| `PLAY_CARD` | `{ "card": { "suit": "OROS", "rank": "REY" } }` | Current player's turn |

#### Server → Client Messages

| Message Type | Payload | When Sent |
|--------------|---------|-----------|
| `GAME_STATE` | Full game state | On connect/reconnect |
| `PLAYER_CONNECTED` | `{ "playerId": "..." }` | Player joins/reconnects |
| `PLAYER_DISCONNECTED` | `{ "playerId": "..." }` | Player disconnects |
| `SOLEDAD_PASSED` | `{ "playerId": "...", "remainingPlayers": [...] }` | Player passes on Soledad |
| `SOLEDAD_AUTO_PASSED` | `{ "playerId": "...", "reason": "TIMEOUT", "remainingPlayers": [...] }` | Player auto-passed after 2min |
| `SOLEDAD_WINDOW_CLOSED` | `{ "declared": false, "awaitingTrumpFrom": "..." }` | All players passed |
| `SOLEDAD_DECLARED` | `{ "byPlayer": "..." }` | Soledad declared |
| `TRUMP_SELECTED` | `{ "suit": "COPAS", "byPlayer": "..." }` | Trump chosen |
| `CARD_PLAYED` | `{ "playerId": "...", "card": {...} }` | Card played |
| `BASA_WON` | `{ "winner": "...", "cards": [...] }` | Trick completed |
| `ROUND_ENDED` | `{ "result": "WIN" | "DRAW", "winners": [...], "coinChanges": {...} }` | 5 basas reached or 4-4 draw |
| `GAME_ENDED` | `{ "reason": "PLAYER_BANKRUPT", "finalCoins": {...} }` | Game over |
| `ERROR` | `{ "code": "...", "message": "..." }` | Invalid action |
| `WAITING_FOR_PLAYER` | `{ "playerId": "...", "since": "..." }` | Player disconnected mid-game |

#### Error Handling: Illegal Moves

When a client attempts an illegal action, the server responds with an `ERROR` message **only to that client** (not broadcast). The game state remains unchanged.

**Error Codes:**

| Code | Meaning | Example Scenario |
|------|---------|------------------|
| `NOT_YOUR_TURN` | Player acted out of turn | Playing card when it's another player's turn |
| `INVALID_CARD` | Card not in player's hand | Playing a card they don't have |
| `MUST_FOLLOW_SUIT` | Must play led suit but didn't | Has Copas but played Oros when Copas was led |
| `INVALID_TRUMP_SELECTION` | Invalid trump choice | Selecting trump when not the "goes" player |
| `INVALID_GAME_STATE` | Action not valid in current state | Playing card before trump is selected |
| `SOLEDAD_NOT_ALLOWED` | Can't declare Soledad now | Declaring after Soledad window closed |
| `ALREADY_PASSED_SOLEDAD` | Player already passed | Sending SOLEDAD_PASS twice |
| `SOLEDAD_WINDOW_CLOSED` | Soledad window no longer open | Passing after someone declared or all passed |

**Example Error Response:**
```json
{
  "type": "ERROR",
  "payload": {
    "code": "MUST_FOLLOW_SUIT",
    "message": "You must play Copas (you have: 3 de Copas, 7 de Copas)",
    "attemptedAction": {
      "type": "PLAY_CARD",
      "card": { "suit": "OROS", "rank": "REY" }
    }
  }
}
```

**Client Recovery:**
- Client should display error to user
- Client state remains unchanged (no optimistic updates for game actions)
- Client can retry with a valid action

### 4.3 Endpoint Testability Matrix

| Endpoint | Unit Test | In-Memory Integration | Docker Integration |
|----------|-----------|----------------------|-------------------|
| POST /auth/anonymous | ✓ Token generation | ✓ Full flow | - |
| GET /auth/me | ✓ Token parsing | ✓ Full flow | - |
| POST /rooms | ✓ Room creation logic | ✓ With in-memory store | ✓ With Postgres |
| GET /rooms | ✓ Filtering logic | ✓ With in-memory store | ✓ With Postgres |
| POST /rooms/{id}/join | ✓ Join validation | ✓ Full flow + game trigger | ✓ With Postgres |
| WS /game/{id} | ✓ Message parsing | ✓ With in-memory game | ✓ Full E2E |
| WS SOLEDAD_PASS | ✓ Pass tracking logic | ✓ All-pass triggers trump phase | ✓ Full E2E |
| WS DECLARE_SOLEDAD | ✓ Declaration logic | ✓ Closes window, sets mode | ✓ Full E2E |
| WS SELECT_TRUMP | ✓ Trump validation | ✓ State transition | ✓ Full E2E |
| WS PLAY_CARD | ✓ Move validation | ✓ Full basa flow | ✓ Full E2E |

---

## 5. Domain Model (Game Context)

### 5.1 Core Entities

```
Game (Aggregate Root)
├── gameId: GameId
├── players: List<Player> (exactly 5, fixed order)
├── rounds: List<Round>
├── currentRound: Round (nullable, null before first round)
├── status: GameStatus (WAITING_FOR_TRUMP | IN_PROGRESS | ENDED)
├── coinBalances: Map<PlayerId, Integer>
└── posso: int (the pot in the middle of the table)

Round
├── roundNumber: int
├── trumpSuit: Suit (nullable until selected)
├── playerWhoGoes: PlayerId
├── soledadPasses: Set<PlayerId> (players who passed, empty until all pass or someone declares)
├── soledadDeadline: Instant (2 minutes from round start, for auto-pass)
├── soledadPlayer: PlayerId (nullable, set if someone declares)
├── basas: List<Basa>
├── currentBasa: Basa (nullable)
├── hands: Map<PlayerId, List<Card>> (private to each player)
├── teams: Teams (nullable; the 2-v-3 case only)
├── mode: RoundMode (HELPED | SELF_KING | SOLEDAD | FOUR_KINGS | KING_FELL, nullable
│                    until a King is played or Soledad is declared)
├── soloPlayer: PlayerId (nullable; the one against four)
├── forcedKingPlayer: PlayerId (nullable; "et cau el rei", pays 1)
├── firstKingCalled: boolean ("es primer rei aida")
├── status: RoundStatus
└── result: RoundResult (nullable until round ends)

RoundResult: GoingSideWon | GoingSideFailed | FourKings | KingFell. There is no draw.

The going side needs 5 basas; the opposing side needs only 4 to put that out of reach, and
the round ends the moment it has them. A going side that reaches 5 having won every basa so
far plays on, because "fer todo" is still worth a point.

RoundStatus: WAITING_FOR_SOLEDAD → WAITING_FOR_TRUMP → IN_PROGRESS → COMPLETE

Basa (Trick)
├── basaNumber: int
├── startingPlayer: PlayerId
├── cardsPlayed: List<PlayedCard> (ordered by play sequence)
├── winner: PlayerId (nullable until resolved)
└── status: BasaStatus

PlayedCard
├── playerId: PlayerId
├── card: Card
└── playedAt: Instant

Card (Value Object)
├── suit: Suit (COPAS | OROS | ESPADAS | BASTOS)
└── rank: Rank (ACE | TWO | THREE | ... | SOTA | CABALLO | REY)

Teams (Value Object)
├── teamOfTwo: Set<PlayerId>
└── teamOfThree: Set<PlayerId>
```

### 5.2 Domain Events

```
GameStarted { gameId, players, startingPlayer }
SoledadPassed { gameId, roundNumber, playerId }
SoledadAutoPassed { gameId, roundNumber, playerId, reason }
SoledadWindowClosed { gameId, roundNumber }
SoledadDeclared { gameId, roundNumber, byPlayer }
TrumpSelected { gameId, roundNumber, suit, byPlayer }
CardPlayed { gameId, roundNumber, basaNumber, playerId, card }
BasaWon { gameId, roundNumber, basaNumber, winner, cards }
SideDecided { gameId, roundNumber, mode, goingSide, opposingSide, byPlayer, king, forced }
RoundEnded { gameId, roundNumber, result, coinChanges, newBalances, posso }
GameEnded { gameId, reason, finalCoinBalances }
PlayerDisconnected { gameId, playerId, timestamp }
PlayerReconnected { gameId, playerId, timestamp }
```

### 5.3 Key Domain Rules (Encapsulated)

**CardRankingService**
- Determines card order based on trump suit
- Handles Espadilla/Basto special rules
- Compares two cards given trump and led suit

**MoveValidator**
- Validates if a card can be played given:
    - Player's hand
    - Led suit of current basa
    - Trump suit
    - Espadilla/Basto bypass rules

**SoledadValidator**
- Tracks which players have passed
- Validates pass/declare actions against current state
- Determines when window closes (all passed or someone declared)
- Handles 2-minute timeout: auto-pass for unresponsive players

**BasaResolver**
- Determines winner of a basa
- Accounts for trump, led suit, special cards

**Round.withKingPlayed** (replaced the former TeamResolver)
- The first King decides the round's shape, all three cases together:
  another player aids (HELPED), the mà puts their own King (SELF_KING),
  or the mà's King is forced out and the hand ends (KING_FELL)
- A forced King is recorded against its owner, who pays 1

**SettlementCalculator**
- Prices a completed round as one base plus +1 increments
- Collections and payments run against the posso, and do not balance

---

## 6. Persistence Strategy

### 6.1 Data Model

**Tables**:

```sql
-- Player identity (optional, for registered users)
-- Managed by existing auth library

-- Active and completed games
games (
    game_id UUID PRIMARY KEY,
    status VARCHAR(20),  -- ACTIVE, COMPLETED, ABANDONED
    created_at TIMESTAMP,
    ended_at TIMESTAMP,
    end_reason VARCHAR(50)
)

-- Players in a game (fixed at game start)
game_players (
    game_id UUID REFERENCES games,
    player_id UUID,
    seat_position INT,  -- 0-4, determines turn order
    display_name VARCHAR(100),
    is_anonymous BOOLEAN,
    PRIMARY KEY (game_id, player_id)
)

-- Current state snapshot (updated on every action)
game_state (
    game_id UUID PRIMARY KEY REFERENCES games,
    state_json JSONB,  -- Full serialized game state
    version INT,  -- Optimistic locking
    updated_at TIMESTAMP
)

-- Event log (append-only, for audit and replay)
game_events (
    event_id UUID PRIMARY KEY,
    game_id UUID REFERENCES games,
    sequence_number INT,
    event_type VARCHAR(50),
    event_data JSONB,
    created_at TIMESTAMP,
    UNIQUE (game_id, sequence_number)
)

-- Room management (pre-game)
rooms (
    room_id UUID PRIMARY KEY,
    status VARCHAR(20),  -- WAITING, STARTING, CONVERTED, ABANDONED
    created_at TIMESTAMP,
    game_id UUID REFERENCES games  -- Set when game starts
)

room_players (
    room_id UUID REFERENCES rooms,
    player_id UUID,
    display_name VARCHAR(100),
    joined_at TIMESTAMP,
    PRIMARY KEY (room_id, player_id)
)
```

### 6.2 Persistence Flow

**On Card Played**:
1. Validate move in domain
2. Apply to in-memory state
3. Within transaction:
    - Append event to game_events
    - Update game_state snapshot
4. Broadcast to connected players
5. If DB write fails, reject the move (no partial state)

**On Server Start**:
1. Query all games with status=ACTIVE
2. For each: load game_state snapshot into memory
3. Mark as "waiting for reconnection"
4. Players reconnect via WebSocket, receive full state

**On Player Reconnect**:
1. Load game from memory (hot state)
2. Send full GAME_STATE message
3. Resume normal play

---

## 7. Implementation Phases

Each phase is independently deployable and testable.

### Phase 1: Identity Foundation
**Scope**: Anonymous player creation, JWT integration
**Deliverables**:
- step 1 POST /api/auth/anonymous endpoint
- step 2 GET /api/auth/me endpoint
- step 3 JWT validation filter
- step 4 In-memory token store (for anonymous session tracking)
  **Tests**:
- Unit: Token generation, parsing
- Integration: Full auth flow
  **Exit Criteria**: Can create anonymous identity, validate JWT, retrieve identity

---

### Phase 2: Lobby - Room Management
**Scope**: Room CRUD, player join/leave
**Deliverables**:
- step 1 Room domain model
- step 2 /room POST
- step 3 /room GET
- step 4 /room/{id}/join POST
- step 5 /room/{id}/leave POST
- use In-memory room repository
- use Room lifecycle (WAITING → STARTING)
  **Tests**:
- Unit: Room join logic, player limits
- Integration: Full room lifecycle
  **Exit Criteria**: 5 players can create/join room, room transitions to STARTING

---

### Phase 3: Game Engine - Core Domain
**Scope**: Pure game logic, no I/O
**Deliverables**:
- Card, Deck, Game, Round, Basa entities
- CardRankingService (all 4 trump scenarios)
- MoveValidator
- BasaResolver
- TeamResolver
- Game state machine
  **Tests**:
- Extensive unit tests for all rule combinations
- Property-based tests for card ranking
  **Exit Criteria**: Can simulate complete game in unit tests

---

### Phase 4: WebSocket Integration
**Scope**: Real-time game communication
**Deliverables**:
- WebSocket configuration with STOMP
- /ws/game/{gameId} endpoint
- Message types (client→server, server→client)
- Connection tracking
- Game event broadcasting
- Room → Game transition trigger
  **Tests**:
- WebSocket connection tests
- Message serialization tests
- Multi-client broadcast tests
  **Exit Criteria**: 5 clients can connect, play cards, see real-time updates

---

### Phase 5: Persistence & Recovery
**Scope**: PostgreSQL storage, recovery flows
**Deliverables**:
- PostgreSQL schema
- JdbcTemplate repositories
- Event logging
- State snapshot updates
- Server restart recovery
- Player reconnection with state sync
  **Tests**:
- Repository tests with Testcontainers
- Recovery scenario tests
- Concurrent access tests
  **Exit Criteria**: Game survives server restart, players can reconnect

---

### Phase 6: Hardening
**Scope**: Rate limiting, error handling, edge cases
**Deliverables**:
- Rate limiting (connection, actions, room creation)
- Graceful error responses
- Timeout handling (waiting for disconnected player)
- Game abandonment detection
  **Tests**:
- Rate limit tests
- Error scenario tests
- Timeout behavior tests
  **Exit Criteria**: System handles abuse gracefully

---

## 8. Design Decisions (Resolved)

| Decision | Resolution |
|----------|------------|
| Disconnect timeout | 5 minutes - then a bot plays the seat so the table can finish; the seat goes back on reconnect |
| Soledad pass timeout | 2 minutes - auto-pass if player doesn't respond |
| Room expiry | 30 minutes - empty/partial rooms auto-deleted |
| Anonymous identity cleanup | 24 hours of inactivity |
| Game state size | Keep full round history in snapshot |
| Event replay capability | Not needed - snapshot sufficient for recovery |
| Concurrent games per player | No - one active game per player |

**How the timeouts run.** Every deadline above is written down when it is set and noticed
by a sweep, not scheduled as a timer. One `@EnableScheduling` (`shared/config/SchedulingConfig`)
and a small scheduled adapter per context - `SoledadTimeoutScheduler`,
`AbandonedSeatScheduler`, `RoomExpiryScheduler` - each asking its own use case what has
expired. The sweep interval and the two policy durations are configuration
(`ginebra.timeouts.*`); the Soledad window's two minutes stay in the domain, because the
deadline is stamped onto the round as it is dealt.

Nothing is precise to the second, and nothing needs to be: the interval is the worst case
by which a stuck table gets moving again. The gain is that no timer has to be cancelled,
rescheduled or rebuilt - which is what makes this survive Phase 5, where the deadlines
come back off a database after a restart.

---

## 9. Package Structure

```
com.ginebra
├── identity/
│   ├── domain/
│   │   └── PlayerId, PlayerIdentity
│   ├── application/
│   │   └── AnonymousAuthService, TokenService
│   ├── adapter/
│   │   ├── in/
│   │   │   └── AuthController
│   │   └── out/
│   │       └── JwtTokenAdapter, InMemorySessionStore
│   └── port/
│       ├── in/
│       │   └── CreateAnonymousUseCase, GetIdentityUseCase
│       └── out/
│           └── TokenGenerator, SessionRepository
│
├── lobby/
│   ├── domain/
│   │   └── Room, RoomStatus, RoomPlayer
│   ├── application/
│   │   └── RoomService
│   ├── adapter/
│   │   ├── in/
│   │   │   └── RoomController
│   │   └── out/
│   │       └── InMemoryRoomRepository, PostgresRoomRepository
│   └── port/
│       ├── in/
│       │   └── CreateRoomUseCase, JoinRoomUseCase, etc.
│       └── out/
│           └── RoomRepository, GameStarter
│
├── game/
│   ├── domain/
│   │   ├── model/
│   │   │   └── Game, Round, Basa, Card, Suit, Rank, Teams
│   │   ├── service/
│   │   │   └── CardRankingService, MoveValidator, BasaResolver
│   │   └── event/
│   │       └── GameEvent, CardPlayed, BasaWon, etc.
│   ├── application/
│   │   └── GameService, GameActionHandler
│   ├── adapter/
│   │   ├── in/
│   │   │   └── GameWebSocketHandler
│   │   └── out/
│   │       └── InMemoryGameRepository, PostgresGameRepository
│   └── port/
│       ├── in/
│       │   └── PlayCardUseCase, SelectTrumpUseCase, etc.
│       └── out/
│           └── GameRepository, GameEventPublisher
│
├── connection/
│   ├── domain/
│   │   └── PlayerConnection, ConnectionStatus
│   ├── application/
│   │   └── ConnectionTracker
│   └── adapter/
│       └── WebSocketSessionAdapter
│
├── persistence/
│   ├── adapter/
│   │   └── PostgresGameStateAdapter, EventLogAdapter
│   └── port/
│       └── GameStatePersistence, EventLog
│
└── shared/
    ├── config/
    │   └── WebSocketConfig, SecurityConfig, DatabaseConfig
    └── infrastructure/
        └── RateLimiter, ErrorHandler
```

---

## 10. Next Steps

1. **Review this design** - Identify gaps, disagreements, or questions
2. **Finalize open questions** - Especially disconnect timeout and concurrent game policy
3. **Set up project skeleton** - Spring Boot project with package structure
4. **Begin Phase 1** - Identity foundation

---

## Appendix A: Message Examples

### Client → Server: Play Card
```json
{
  "type": "PLAY_CARD",
  "payload": {
    "card": {
      "suit": "OROS",
      "rank": "REY"
    }
  }
}
```

### Server → Client: Game State (on connect)
```json
{
  "type": "GAME_STATE",
  "payload": {
    "gameId": "uuid",
    "status": "IN_PROGRESS",
    "players": [
      { "id": "uuid", "name": "Player1", "seatPosition": 0, "connected": true }
    ],
    "coinBalances": { "player1-uuid": 20, "player2-uuid": 18 },
    "currentRound": {
      "roundNumber": 1,
      "status": "WAITING_FOR_SOLEDAD",
      "trumpSuit": null,
      "playerWhoGoes": "player1-uuid",
      "soledadPasses": ["player2-uuid", "player3-uuid"],
      "soledadDeadline": "2026-01-10T15:32:00Z",
      "soledadPlayer": null,
      "yourHand": [ { "suit": "OROS", "rank": "REY" } ],
      "currentBasa": null,
      "basasWon": {},
      "teams": null
    }
  }
}
```

**Note:** `soledadDeadline` is the timestamp when remaining players will be auto-passed. Clients can use this to show a countdown. When `status` is `IN_PROGRESS`, fields like `currentBasa` will be populated instead.

### Server → Client: Card Played
```json
{
  "type": "CARD_PLAYED",
  "payload": {
    "playerId": "uuid",
    "card": { "suit": "ESPADAS", "rank": "AS" },
    "nextTurn": "next-player-uuid"
  }
}
```

### Client → Server: Soledad Pass
```json
{
  "type": "SOLEDAD_PASS",
  "payload": {}
}
```

### Server → Client: Soledad Passed
```json
{
  "type": "SOLEDAD_PASSED",
  "payload": {
    "playerId": "player3-uuid",
    "remainingPlayers": ["player4-uuid", "player5-uuid"]
  }
}
```

### Server → Client: Soledad Window Closed
```json
{
  "type": "SOLEDAD_WINDOW_CLOSED",
  "payload": {
    "declared": false,
    "awaitingTrumpFrom": "player2-uuid"
  }
}
```

### Server → Client: Soledad Auto-Passed (timeout)
```json
{
  "type": "SOLEDAD_AUTO_PASSED",
  "payload": {
    "playerId": "player5-uuid",
    "reason": "TIMEOUT",
    "remainingPlayers": ["player1-uuid"]
  }
}
```

---

## Appendix B: Complete Game Flow Example

This example traces through a complete first basa (trick), demonstrating state transitions, message flows, and rule validation.

### Scenario Setup

**Players (seated clockwise):**
- Seat 0: P1
- Seat 1: P2 (has Espadilla - will "go" first)
- Seat 2: P3
- Seat 3: P4
- Seat 4: P5

**Outcome:** P2 selects COPAS as trump, leads with 6♣, and P1 wins the basa by "refalla" with Horse♥.

---

### Phase 1: Room → Game Transition

```
ROOM STATE (before 5th player joins)
┌─────────────────────────────────────┐
│ Room: abc-123                       │
│ Status: WAITING                     │
│ Players: [P1, P2, P3, P4]          │
└─────────────────────────────────────┘

Player 5 joins via POST /api/rooms/abc-123/join
         │
         ▼
┌─────────────────────────────────────┐
│ Room: abc-123                       │
│ Status: STARTING                    │
│ Players: [P1, P2, P3, P4, P5]      │
└─────────────────────────────────────┘
         │
         │ Triggers game creation
         ▼
┌─────────────────────────────────────┐
│ Game: xyz-789 created               │
│ Players assigned seats 0-4         │
│ Seat 0: P1                          │
│ Seat 1: P2                          │
│ Seat 2: P3                          │
│ Seat 3: P4                          │
│ Seat 4: P5                          │
│ Coins: {P1: 20, P2: 20, ...}       │
└─────────────────────────────────────┘
```

**Response to Player 5's join request:**
```json
{
  "roomId": "abc-123",
  "status": "STARTING",
  "gameId": "xyz-789",
  "websocketUrl": "/ws/game/xyz-789"
}
```

All 5 clients now connect to WebSocket.

---

### Phase 2: Round Setup - Card Dealing

```
SERVER: Shuffle deck, deal 8 cards to each player

Player hands dealt:
┌────────────────────────────────────────────────────────┐
│ P1: [5♦, K♥, Horse♥, 2♠, 4♠, 6♠, Sota♦, 7♦]          │
│ P2: [A♠(Espadilla), 6♣, 3♥, 7♦, K♣, 2♥, 5♠, 4♦]      │  ← Has Espadilla!
│ P3: [5♥, 7♥, A♦, 2♦, K♠, Horse♠, 3♦, 6♦]             │
│ P4: [7♣, 4♣, A♥, Horse♦, 6♦, 3♠, Sota♠, 2♣]          │
│ P5: [A♣(Basto), K♦, 4♥, 6♥, Horse♣, Sota♥, 5♣, 7♠]   │
└────────────────────────────────────────────────────────┘

SERVER: Detect Espadilla holder → P2 "goes" (starts first round)
```

**Broadcast to all players: GAME_STATE**

Each player receives personalized state (only sees their own hand):

```json
{
  "type": "GAME_STATE",
  "payload": {
    "gameId": "xyz-789",
    "status": "IN_PROGRESS",
    "players": [
      { "id": "p1", "name": "Player1", "seat": 0, "connected": true },
      { "id": "p2", "name": "Player2", "seat": 1, "connected": true },
      { "id": "p3", "name": "Player3", "seat": 2, "connected": true },
      { "id": "p4", "name": "Player4", "seat": 3, "connected": true },
      { "id": "p5", "name": "Player5", "seat": 4, "connected": true }
    ],
    "coinBalances": { "p1": 20, "p2": 20, "p3": 20, "p4": 20, "p5": 20 },
    "currentRound": {
      "roundNumber": 1,
      "playerWhoGoes": "p2",
      "trumpSuit": null,
      "status": "WAITING_FOR_SOLEDAD",
      "soledadPasses": [],
      "soledadPlayer": null,
      "yourHand": ["...player's 8 cards..."]
    }
  }
}
```

---

### Phase 3: Soledad Window (Explicit Pass)

```
Game State: WAITING_FOR_SOLEDAD
           │
           │ Each player must explicitly pass or declare Soledad
           │ Server waits for all 5 players to respond
           │ 2-minute timeout per player: auto-pass if no response
           │
           ▼
```

**P1 → Server:**
```json
{ "type": "SOLEDAD_PASS", "payload": {} }
```

**Broadcast: SOLEDAD_PASSED**
```json
{
  "type": "SOLEDAD_PASSED",
  "payload": {
    "playerId": "p1",
    "remainingPlayers": ["p2", "p3", "p4", "p5"]
  }
}
```

**P2, P3, P4 each send SOLEDAD_PASS...**

After each pass, server broadcasts SOLEDAD_PASSED with updated remainingPlayers.

**P5 disconnects or doesn't respond within 2 minutes:**

**Broadcast: SOLEDAD_AUTO_PASSED**
```json
{
  "type": "SOLEDAD_AUTO_PASSED",
  "payload": {
    "playerId": "p5",
    "reason": "TIMEOUT",
    "remainingPlayers": []
  }
}
```

**Server detects all 5 have passed (manually or auto):**

```
Round.soledadPasses = [P1, P2, P3, P4, P5]
Round.status = WAITING_FOR_TRUMP
```

**Broadcast: SOLEDAD_WINDOW_CLOSED**
```json
{
  "type": "SOLEDAD_WINDOW_CLOSED",
  "payload": {
    "declared": false,
    "awaitingTrumpFrom": "p2"
  }
}
```

**Note on Timeout Behavior:**
- Each player has 2 minutes from round start to pass or declare
- Timeout only applies to Soledad window, not regular gameplay
- During regular gameplay (card play), 5-minute disconnect timeout applies instead
- Auto-pass allows game to progress even if a player is temporarily AFK

---

### Phase 4: Trump Selection

```
Game State: WAITING_FOR_TRUMP
           │
           │ P2 (who "goes") must select trump
           ▼
```

**P2 → Server:**
```json
{
  "type": "SELECT_TRUMP",
  "payload": { "suit": "COPAS" }
}
```

**Server validates:**
- Is it P2's turn to select? ✓
- Is COPAS a valid suit? ✓

**Server updates state:**
```
Round.trumpSuit = COPAS
Round.status = IN_PROGRESS
CurrentBasa = new Basa(number=1, startingPlayer=P2)
```

**Broadcast: TRUMP_SELECTED**
```json
{
  "type": "TRUMP_SELECTED",
  "payload": {
    "suit": "COPAS",
    "byPlayer": "p2",
    "currentTurn": "p2",
    "basaNumber": 1
  }
}
```

**Card Ranking now follows COPAS rules:**
```
COPAS (Trump) ranking:
1. Espadilla (A♠)
2. 7♥ (Manilla)
3. Basto (A♣)
4. A♥
5. K♥
6. Horse♥  ← Player 1 has this
7. Sota♥
8. 2♥
9. 3♥
10. 4♥
11. 5♥     ← Player 3 will play this
12. 6♥
```

---

### Phase 5: First Basa (Trick)

#### Turn 1: P2 leads with 6 of Bastos

```
Current Basa State:
┌─────────────────────────────────────┐
│ Basa #1                             │
│ Led suit: (none yet)                │
│ Cards: []                           │
│ Current turn: P2                    │
└─────────────────────────────────────┘
```

**P2 → Server:**
```json
{
  "type": "PLAY_CARD",
  "payload": { "card": { "suit": "BASTOS", "rank": "SIX" } }
}
```

**Server validates:**
- Is it P2's turn? ✓
- Does P2 have 6♣? ✓
- As first card, any card is valid ✓

**Server updates:**
```
Basa.ledSuit = BASTOS
Basa.cardsPlayed = [{player: P2, card: 6♣}]
P2.hand.remove(6♣)
currentTurn = P3 (clockwise)
```

**Broadcast: CARD_PLAYED**
```json
{
  "type": "CARD_PLAYED",
  "payload": {
    "playerId": "p2",
    "card": { "suit": "BASTOS", "rank": "SIX" },
    "nextTurn": "p3",
    "ledSuit": "BASTOS"
  }
}
```

---

#### Turn 2: P3 has no Bastos, "falla" with 5♥

```
P3's hand: [5♥, 7♥, A♦, 2♦, K♠, Horse♠, 3♦, 6♦]
           (No Bastos cards - can play anything)
```

**P3 → Server:**
```json
{
  "type": "PLAY_CARD",
  "payload": { "card": { "suit": "COPAS", "rank": "FIVE" } }
}
```

**Server validates:**
- Is it P3's turn? ✓
- Does P3 have 5♥? ✓
- Can P3 play this card?
    - Led suit is BASTOS
    - Does P3 have any BASTOS? Check hand... NO ✓
    - P3 can play any card ✓
    - Playing trump (COPAS) = "fallar" ✓

**Server updates:**
```
Basa.cardsPlayed = [{P2, 6♣}, {P3, 5♥}]
P3.hand.remove(5♥)
currentTurn = P4
currentWinner = P3 (trump beats non-trump)
```

**Broadcast: CARD_PLAYED**
```json
{
  "type": "CARD_PLAYED",
  "payload": {
    "playerId": "p3",
    "card": { "suit": "COPAS", "rank": "FIVE" },
    "nextTurn": "p4",
    "isTrump": true,
    "currentWinner": "p3"
  }
}
```

---

#### Turn 3: P4 has Bastos, must follow suit

```
P4's hand: [7♣, 4♣, A♥, Horse♦, 6♦, 3♠, Sota♠, 2♣]
           Has: 7♣, 4♣, 2♣ (Bastos cards)
           MUST play one of these
```

**P4 → Server:**
```json
{
  "type": "PLAY_CARD",
  "payload": { "card": { "suit": "BASTOS", "rank": "SEVEN" } }
}
```

**Server validates:**
- Is it P4's turn? ✓
- Does P4 have 7♣? ✓
- Can P4 play this card?
    - Led suit is BASTOS
    - Does P4 have BASTOS? YES (7♣, 4♣, 2♣)
    - Is played card BASTOS? YES ✓

Note: P4 is NOT required to "kill" - playing 7♣ that doesn't beat 6♣ is legal.

**Server updates:**
```
Basa.cardsPlayed = [{P2, 6♣}, {P3, 5♥}, {P4, 7♣}]
currentWinner = P3 (trump still beats non-trump)
```

**Broadcast: CARD_PLAYED**
```json
{
  "type": "CARD_PLAYED",
  "payload": {
    "playerId": "p4",
    "card": { "suit": "BASTOS", "rank": "SEVEN" },
    "nextTurn": "p5",
    "currentWinner": "p3"
  }
}
```

---

#### Turn 4: P5 has Bastos, must follow suit

```
P5's hand: [A♣(Basto), K♦, 4♥, 6♥, Horse♣, Sota♥, 5♣, 7♠]

Important: A♣ is the BASTO (special card)
- Basto does NOT belong to Bastos for following purposes
- P5 has: Horse♣, 5♣ as regular Bastos
- MUST play one of these (Basto can be saved for later)
```

**P5 → Server:**
```json
{
  "type": "PLAY_CARD",
  "payload": { "card": { "suit": "BASTOS", "rank": "HORSE" } }
}
```

**Server validates:**
- P5 has regular Bastos (excluding Basto special card)? YES (Horse♣, 5♣)
- Playing Horse♣ (regular Bastos)? ✓

**Server updates:**
```
Basa.cardsPlayed = [{P2, 6♣}, {P3, 5♥}, {P4, 7♣}, {P5, Horse♣}]
currentWinner = P3 (trump still winning)
```

**Broadcast: CARD_PLAYED**
```json
{
  "type": "CARD_PLAYED",
  "payload": {
    "playerId": "p5",
    "card": { "suit": "BASTOS", "rank": "HORSE" },
    "nextTurn": "p1",
    "currentWinner": "p3"
  }
}
```

---

#### Turn 5: P1 has no Bastos, "refalla" with Horse♥

```
P1's hand: [5♦, K♥, Horse♥, 2♠, 4♠, 6♠, Sota♦, 7♦]
           (No Bastos cards - can play anything)
```

**P1 → Server:**
```json
{
  "type": "PLAY_CARD",
  "payload": { "card": { "suit": "COPAS", "rank": "HORSE" } }
}
```

**Server validates:**
- Does P1 have Bastos? NO ✓
- Can play any card ✓
- Playing Horse♥ (trump) = "refallar" (beat existing trump)

**Card comparison (both trump - COPAS):**
```
P3 played: 5♥  → Rank 11 in COPAS trump
P1 played: Horse♥ → Rank 6 in COPAS trump

Horse♥ (rank 6) beats 5♥ (rank 11)
Winner: P1
```

---

### Phase 6: Basa Resolution

**Server determines winner:**
```
Cards played:
  P2: 6♣ (Bastos, non-trump) 
  P3: 5♥ (COPAS trump, rank 11)
  P4: 7♣ (Bastos, non-trump)
  P5: Horse♣ (Bastos, non-trump)
  P1: Horse♥ (COPAS trump, rank 6) ← WINNER (highest trump)
```

**Server updates:**
```
Basa #1:
  winner = P1
  status = COMPLETE
  
Round state:
  basasWon = {P1: 1, P2: 0, P3: 0, P4: 0, P5: 0}
  
Next basa:
  Basa #2 starting player = P1 (the winner of basa #1)
```

**Broadcast: BASA_WON**
```json
{
  "type": "BASA_WON",
  "payload": {
    "basaNumber": 1,
    "winner": "p1",
    "winningCard": { "suit": "COPAS", "rank": "HORSE" },
    "allCards": [
      { "playerId": "p2", "card": { "suit": "BASTOS", "rank": "SIX" } },
      { "playerId": "p3", "card": { "suit": "COPAS", "rank": "FIVE" } },
      { "playerId": "p4", "card": { "suit": "BASTOS", "rank": "SEVEN" } },
      { "playerId": "p5", "card": { "suit": "BASTOS", "rank": "HORSE" } },
      { "playerId": "p1", "card": { "suit": "COPAS", "rank": "HORSE" } }
    ],
    "basasWon": { "p1": 1, "p2": 0, "p3": 0, "p4": 0, "p5": 0 },
    "nextBasa": {
      "basaNumber": 2,
      "startingPlayer": "p1"
    }
  }
}
```

---

### Summary State After Basa 1

```
┌─────────────────────────────────────────────────────────┐
│ GAME STATE                                              │
├─────────────────────────────────────────────────────────┤
│ Round: 1                                                │
│ Trump: COPAS                                            │
│ Player who "goes": P2                                   │
│ Teams: NOT YET REVEALED (no King played)               │
│                                                         │
│ Basas won:                                              │
│   P1: 1  |  P2: 0  |  P3: 0  |  P4: 0  |  P5: 0        │
│                                                         │
│ Cards remaining in hands: 7 each                        │
│                                                         │
│ Next basa (#2) starts with: P1                         │
│ (the winner of basa #1)                                │
└─────────────────────────────────────────────────────────┘
```

---

### Key Validation Points Demonstrated

| Rule | Example in Flow |
|------|-----------------|
| Espadilla holder starts first round | P2 has A♠, P2 "goes" |
| Explicit Soledad pass required | All 5 players sent SOLEDAD_PASS |
| 2-minute auto-pass on timeout | P5 auto-passed due to timeout |
| "Goes" player selects trump | P2 selects COPAS |
| Must follow led suit if able | P4, P5 had Bastos, played Bastos |
| Can "fallar" if no led suit | P3 had no Bastos, played trump |
| Can "refallar" to beat trump | P1 had no Bastos, beat P3's trump |
| Not required to "kill" | P4 played 7♣ even though 6♣ was led |
| Next basa is led by the winner of the previous one | Basa 2 starts with P1, who won basa 1 |
| Basto/Espadilla are "free" (don't count as their suit) | P5 had Basto but still had to play regular Bastos |

---

### State Machine Summary

```
ROOM_WAITING
    │ (5th player joins)
    ▼
ROOM_STARTING ──────────────────► GAME CREATED
                                      │
                                      ▼
                              WAITING_FOR_SOLEDAD
                                      │
                    ┌─────────────────┼─────────────────┐
                    │ (all pass)      │ (someone declares)
                    ▼                 ▼                 │
              WAITING_FOR_TRUMP   SOLEDAD_DECLARED ────┘
                    │                 │
                    └────────┬────────┘
                             │ (trump selected)
                             ▼
                        IN_PROGRESS
                             │
                    ┌────────┴────────┐
                    │                 │
              (play basas)      (5 basas or 4-4)
                    │                 │
                    └────────┬────────┘
                             ▼
                       ROUND_ENDED
                             │
                    ┌────────┴────────┐
                    │                 │
              (coins > 0)      (someone bankrupt)
                    │                 │
                    ▼                 ▼
           NEW ROUND START       GAME_ENDED
```