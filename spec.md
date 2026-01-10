# Online Card Game - Technical Specification

## Version History
- v0.1 - Initial draft (2026-01-10)

---

## 1. Non-Functional Requirements

### 1.1 Performance & Scale

**Load Profile**
- Expected load: 1,000 games per day
- Players per game: 5 players
- Total player sessions: ~5,000 sessions per day
- Peak concurrent games: ~20-50 games
- Peak concurrent players: ~100-250 players

**Response Time**
- Target response time: <500ms for game actions
- Acceptable delays for non-critical updates
- Priority: Functional and stable over blazing fast

**Geographic Distribution**
- Single region deployment
- All players expected to be in same geographic region

### 1.2 Reliability & Availability

**Player Reconnection**
- Players must be able to rejoin after disconnection
- Full game state must be reloaded upon reconnection
- Session should remain active during temporary disconnects

**Server Restart Resilience**
- Game state must survive server restarts
- Active games must resume after server comes back online
- No data loss on unexpected server shutdown

**Session Duration**
- Game duration: 20 minutes to several hours
- Sessions must remain stable throughout entire game duration

### 1.3 User Management

**Authentication**
- Support for registered user accounts
- Support for anonymous play (no account required)
- Both user types have equal gameplay experience

**Data Persistence**
- User accounts and credentials (for registered users)
- Anonymous session tracking (temporary)
- No leaderboards or statistics (for now)
- Game history: TBD

### 1.4 Security

**Anti-Cheat**
- Server authoritative architecture (server is source of truth)
- All game actions validated server-side
- Clients cannot manipulate game state directly

**DOS & Spam Prevention**
- Rate limiting on connection attempts per IP
- Rate limiting on game actions per player
- Rate limiting on game creation per user/IP
- Protection against malicious clients

### 1.5 Data Persistence Strategy

**State Management**
- Hot state: Active games in memory for fast access
- Persistent state: Database backup for recovery
- Game state saved to database on significant actions

**Recovery Scenarios**
- Player reconnection: Load from DB → Restore to memory → Sync to client
- Server restart: Load all active games from DB → Restore to memory → Continue play

---

## 3. Architectural Implications

### 3.1 Required Components

**Database Required**
- Must persist game state to survive server restarts
- Cannot rely on in-memory storage alone
- Technology choice: **PostgreSQL**
    - ACID transactions for game state consistency
    - Structured relational data model
    - Strong data integrity constraints
    - Mature and well-tested

**WebSocket Communication**
- Real-time bidirectional communication between server and clients
- Automatic reconnection handling
- Technology choice: **Spring WebSocket with STOMP protocol**
    - Native Spring Boot integration
    - Pub/sub messaging patterns for broadcasting game updates
    - Built-in session management
    - Room-based communication support
    - SockJS fallback for clients without WebSocket support

**Backend Framework**
- Technology choice: **Java + Spring Boot**
    - Robust ecosystem for enterprise applications
    - Excellent WebSocket support
    - Strong transaction management
    - Built-in security features
    - Easy integration with PostgreSQL

**Session Management**
- Track active game sessions
- Handle player connections and disconnections
- Manage session lifecycle (creation, active, completed, abandoned)

**State Management Architecture**
- **Hot state**: Active games in memory for fast access during gameplay
- **Persistent state**: PostgreSQL backup for recovery
- Game state saved to database on significant actions
- Dual-layer approach ensures both performance and reliability

### 3.2 Technology Stack Summary

**Frontend**
- Framework: React 18+
- Language: TypeScript
- Build Tool: Vite
- WebSocket Client: @stomp/stompjs + sockjs-client
- State Management: Zustand or Redux Toolkit
- Routing: React Router
- Styling: Tailwind CSS (optional)

**Rationale for React + TypeScript:**
- Excellent integration with Spring WebSocket via STOMP protocol
- Rich component ecosystem for interactive card game UI
- Type safety prevents runtime errors in complex game logic
- Strong development tooling and large community support
- Efficient for rapid development and debugging

**Backend**
- Language: Java
- Framework: Spring Boot
- WebSocket: Spring WebSocket + STOMP
- Database: PostgreSQL
- Data Access: Spring Data JDBC (simple SQL, no ORM complexity)

**Communication Layer**
- Protocol: WebSocket (with SockJS fallback)
- Messaging: STOMP for pub/sub patterns
- Session tracking: Spring WebSocket session management

**Data Persistence**
- Primary database: PostgreSQL
- Caching: Spring Cache (optional, for active game state)
- Transaction management: Spring @Transactional

### 3.3 Deployment Considerations

**Single Server Deployment** (sufficient for current scale)
- All components on one server instance
- PostgreSQL co-located or separate database server
- Can handle 50+ concurrent games easily

**Future Scalability Options** (if needed)
- Horizontal scaling: Multiple Spring Boot instances behind load balancer
- Session affinity: Sticky sessions for WebSocket connections
- External message broker: RabbitMQ or ActiveMQ for distributed messaging
- Shared state: Redis for distributed game state cache
- Database replication: PostgreSQL read replicas for scaling reads

---

## 2. Functional Requirements - Game Rules (Ginebra)

### 2.1 Game Overview

**Game Type**: Spanish card trick-taking game for exactly 5 players

**Objective**: Be part of the team that wins 5 basas (tricks) first in a round

**Deck**: Spanish deck (Baraja Española) - 40 cards
- 4 suits: Copas (Cups), Oros (Coins), Espadas (Swords), Bastos (Clubs)
- Each suit: Ace, 2, 3, 4, 5, 6, 7, Sota (Jack), Horse (Knight), King

### 2.2 Game Setup

**Players**
- Exactly 5 players required to start a game
- Players sit in a fixed circular order
- Turn order proceeds clockwise (to the right)

**Initial Coin Distribution**
- Each player starts with 20 coins
- Players contribute coins to the pot before each round (amount TBD)

**Card Distribution**
- Each player receives 8 cards at the start of each round
- 8 cards × 5 players = 40 cards (entire deck is dealt)
- Server shuffles deck before each round

**First Round Start**
- The player holding the Ace of Espadas (Espadilla) starts the first round only
- This establishes the initial turn order
- Subsequent rounds: the player to the right of the previous starting player goes first

### 2.3 Core Gameplay Mechanics

**The Player Who "Goes" (El que Va)**
- The starting player of each round "goes"
- This player selects the trump suit (triumph) for the entire round
- Trump suit determines card rankings and is the "leading color"

**Team Formation (2 vs 3)**
- Game starts with no revealed teams
- Partnership is revealed when the first King (of any suit) is played during the round
- Player who "goes" + player who plays first King = Team of 2
- Remaining 3 players = Opposing team
- **Special case**: If the player who "goes" plays the first King themselves, they can choose to stop the game

**Playing a Basa (Trick)**
1. Starting player plays any card from their hand
2. Play continues clockwise (to the right)
3. Each player must play one card
4. After all 5 cards are played, the highest card wins the basa
5. Winner collects all 5 cards (the basa counts as 1 toward the 5 needed to win)
6. **Next basa starts with the player to the right of the previous starting player** (NOT the basa winner)

**Card Following Rules**
- **Must follow suit**: If a player has the suit that was led, they MUST play that suit
- **Cannot follow suit**: If they don't have the led suit, they can:
    - Play any other suit
    - **Fallar**: Play a trump card (beats non-trump cards)
    - **Refallar**: Beat another player's trump card with a higher trump
- **NOT required to "kill"**: Players can play a lower card of the same suit (don't have to beat previous cards)
- These rules apply throughout the entire round (before and after first King)

**Special Cards: Espadilla and Basto**
- **Ace of Espadas (Espadilla)**: Universal high card, doesn't belong to Espadas suit for following purposes
- **Ace of Bastos (Basto)**: Universal high card, doesn't belong to Bastos suit for following purposes
- These two cards beat all other cards in the game
- When played, they count as trump suit cards (the "leading color")
- They can be played at any time (even when player has the led suit)

### 2.4 Card Ranking System

The ranking system depends entirely on which suit is selected as trump. The tables below show the complete card order (ORDRE) for each suit when it "goes" (VA).

**Key Principles:**
- **Espadilla** (Ace of Espadas) is ALWAYS the highest card regardless of trump
- **Manilla** changes based on trump: 7 for Copas/Oros, 2 for Espadas/Bastos
- **Basto** (Ace of Bastos) is ALWAYS the third highest card
- Trump suit cards beat all non-trump cards
- Within non-trump suits: King > Horse > Sota > Ace (if present) > 7 > 6 > 5 > 4 > 3 > 2

**Note on Manilla**: When a card becomes the Manilla, it occupies position 2 in the ranking and doesn't appear again in its normal position.

---

#### When COPAS "Goes" (Trump)

| Rank | **COPAS (Trump)** | OROS | ESPADAS | BASTOS |
|------|-------------------|------|---------|--------|
| 1 | **As d'Espases (espadilla)** | Rei d'Oros (12) | Rei d'Espases | Rei de Bastos |
| 2 | **7 de Copes (manilla)** | Cavall d'Oros (11) | Cavall d'Espases | Cavall de Bastos |
| 3 | **As de Basto (Basto)** | Sota d'Oros | Sota d'Espases | Sota de Bastos |
| 4 | **As de Copes (Carabassa)** | As d'Oros (Rovell) | 7 d'Espases | 7 de Bastos |
| 5 | **Rei de Copes** | 2 d'Oros | 6 d'Espases | 6 de Bastos |
| 6 | **Cavall de Copes** | 3 d'Oros | 5 d'Espases | 5 de Bastos |
| 7 | **Sota de Copes** | 4 d'Oros | 4 d'Espases | 4 de Bastos |
| 8 | **2 de Copes** | 5 d'Oros | 3 d'Espases | 3 de Bastos |
| 9 | **3 de Copes** | 6 d'Oros | 2 d'Espases | 2 de Bastos |
| 10 | **4 de Copes** | 7 d'Oros | | |
| 11 | **5 de Copes** | | | |
| 12 | **6 de Copes** | | | |
| 13 | **7 de Copes** | | | |

---

#### When OROS "Goes" (Trump)

| Rank | COPAS | **OROS (Trump)** | ESPADAS | BASTOS |
|------|-------|------------------|---------|--------|
| 1 | Rei de Copes | **As d'Espases (espadilla)** | Rei d'Espases | Rei de Bastos |
| 2 | Cavall de Copes | **7 d'Oros (manilla)** | Cavall d'Espases | Cavall de Bastos |
| 3 | Sota de Copes | **As de Bastos (Basto)** | Sota d'Espases | Sota de Bastos |
| 4 | As de Copes (Rovell) | **As d'Oros (Rovell)** | 7 d'Espases | 7 de Bastos |
| 5 | 2 de Copes | **Rei d'Oros** | 6 d'Espases | 6 de Bastos |
| 6 | 3 de Copes | **Cavall d'Oros** | 5 d'Espases | 5 de Bastos |
| 7 | 4 de Copes | **Sota d'Oros** | 4 d'Espases | 4 de Bastos |
| 8 | 5 de Copes | **2 d'Oros** | 3 d'Espases | 3 de Bastos |
| 9 | 6 de Copes | **3 d'Oros** | 2 d'Espases | 2 de Bastos |
| 10 | 7 de Copes | **4 d'Oros** | | |
| 11 | | **5 d'Oros** | | |
| 12 | | **6 d'Oros** | | |
| 13 | | **7 d'Oros** | | |

---

#### When ESPASES "Goes" (Trump)

| Rank | COPAS | OROS | **ESPASES (Trump)** | BASTOS |
|------|-------|------|---------------------|--------|
| 1 | Rei de Copes | Rei d'Oros | **As d'Espases (espadilla)** | Rei de Bastos |
| 2 | Cavall de Copes | Cavall d'Oros | **2 d'Espases (manilla)** | Cavall de Bastos |
| 3 | Sota de Copes | Sota d'Oros | **As de Bastos (Basto)** | Sota de Bastos |
| 4 | As de Copes (Rovell) | As d'Oros (Rovell) | **Rei d'Espases** | 7 de Bastos |
| 5 | 2 de Copes | 2 d'Oros | **Cavall d'Espases** | 6 de Bastos |
| 6 | 3 de Copes | 3 d'Oros | **Sota d'Espases** | 5 de Bastos |
| 7 | 4 de Copes | 4 d'Oros | **7 d'Espases** | 4 de Bastos |
| 8 | 5 de Copes | 5 d'Oros | **6 d'Espases** | 3 de Bastos |
| 9 | 6 de Copes | 6 d'Oros | **5 d'Espases** | 2 de Bastos |
| 10 | 7 de Copes | 7 d'Oros | **4 d'Espases** | | |
| 11 | | | **3 d'Espases** | | |
| 12 | | | **2 d'Espases** | | |

---

#### When BASTOS "Goes" (Trump)

| Rank | COPAS | OROS | ESPADAS | **BASTOS (Trump)** |
|------|-------|------|---------|-------------------|
| 1 | Rei de Copes | Rei d'Oros | Rei d'Espases | **As d'Espases (espadilla)** |
| 2 | Cavall de Copes | Cavall d'Oros | Cavall d'Espases | **2 de Bastos (manilla)** |
| 3 | Sota de Copes | Sota d'Oros | Sota d'Espases | **As de Bastos (Basto)** |
| 4 | As de Copes (Rovell) | As d'Oros (Rovell) | 7 d'Espases | **Rei de Bastos** |
| 5 | 2 de Copes | 2 d'Oros | 6 d'Espases | **Cavall de Bastos** |
| 6 | 3 de Copes | 3 d'Oros | 5 d'Espases | **Sota de Bastos** |
| 7 | 4 de Copes | 4 d'Oros | 4 d'Espases | **7 de Bastos** |
| 8 | 5 de Copes | 5 d'Oros | 3 d'Espases | **6 de Bastos** |
| 9 | 6 de Copes | 6 d'Oros | 2 d'Espases | **5 de Bastos** |
| 10 | 7 de Copes | 7 d'Oros | | **4 de Bastos** |
| 11 | | | | **3 de Bastos** |
| 12 | | | | **2 de Bastos** |

---

**Important Notes:**
1. The three special cards (Espadilla, Manilla, Basto) always occupy the top 3 positions
2. When Espadas or Bastos is trump, their respective 2 becomes the Manilla and doesn't reappear at position 12
3. Non-trump suits have fewer cards in ranking (9 cards each) since their Aces are special
4. The trump suit has all its cards ranked (13 positions for Copas/Oros, 11 for Espadas/Bastos after accounting for special cards)

### 2.5 Special Game Mode: Soledad

**Declaration**
- Before the round starts (after cards are dealt), any player can declare "SOLEDAD"
- The declaring player chooses the trump suit
- The declaring player plays alone: 1 vs 4 (against all other players)

**Turn Order**
- The round still starts with the player whose turn it is according to normal rotation
- The Soledad player doesn't necessarily start the round

**Winning Condition**
- Soledad player must win 5 basas to win the round
- If the Soledad player gets fewer than 5 basas, the other 4 players win

### 2.6 Scoring & Coins

**Round End Condition**
- Round ends immediately when one team wins 5 basas
- Remaining cards/basas are not played
- New round begins

**Coin Distribution - Normal Game (2 vs 3)**
- **Winning team**: Each player on winning team receives 2 coins
- **Losing team**: Each player on losing team loses 2 coins
- **Draw**: Each player receives 1 coin (unclear when draw occurs - TBD)

**Coin Distribution - Soledad**
- **Soledad player wins**: Receives 3 coins from each of the 4 other players (12 coins total)
- **Soledad player loses**: Pays 3 coins to each of the 4 other players (12 coins total)

**Special Combinations Bonus** (awarded at end of round to winning team/player)
- **Duende**: Having Ace of Espadas + Ace of Bastos in hand = +1 coin bonus
- **Estuche**: Having Ace of Espadas + Ace of Bastos + Manilla in hand = +2 coins bonus
- These bonuses are in addition to the regular win rewards
- *Note: These values may be adjusted during development*

### 2.7 Complete Game Flow

```
┌─────────────────────────────────────┐
│     NEW ROUND SETUP                 │
├─────────────────────────────────────┤
│ 1. Deal 8 cards to each player      │
│ 2. Determine starting player:       │
│    - First round: Espadilla holder  │
│    - Subsequent: Clockwise rotation │
│ 3. Soledad declaration (optional)   │
│ 4. Player who "goes" selects trump  │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│     PLAY BASAS (Up to 8 tricks)     │
├─────────────────────────────────────┤
│ For each basa:                      │
│ 1. Starting player plays card       │
│ 2. Play continues clockwise         │
│ 3. Players follow suit if possible  │
│ 4. Highest card wins basa           │
│ 5. First King reveals partnership   │
│ 6. Next basa: player to the right   │
│                                     │
│ Continue until one team wins 5 basas│
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│     ROUND END & SCORING             │
├─────────────────────────────────────┤
│ 1. First team to 5 basas wins       │
│ 2. Calculate coin distribution      │
│ 3. Award special combination bonuses│
│ 4. Update player coin totals        │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│     CONTINUE OR END GAME            │
├─────────────────────────────────────┤
│ - Start new round (if continuing)   │
│ - End game (if players decide/vote) │
└─────────────────────────────────────┘
```

### 2.8 Open Questions (Game Rules)

**Clarifications Needed:**
- [ ] What happens when a player runs out of coins? (Can't play? Borrow? Auto-eliminated?)
- [ ] Is there an overall game winner, or just continuous rounds until players quit?
- [ ] Draw condition: When/how does a draw occur in a round?
- [ ] Exact timing of Soledad declaration - before or after trump selection?
- [ ] Can multiple players declare Soledad? If yes, how to resolve?
- [ ] What happens if the player who "goes" disconnects before choosing trump?
- [ ] Initial pot contribution: How many coins does each player contribute at round start?
- [ ] Can players join/leave between rounds, or must the same 5 play the entire game?
- [ ] Maximum game duration or number of rounds?

---

## 4. Open Questions & Future Considerations

### 4.1 To Be Defined

**Game History**
- [ ] Should completed games be stored permanently?
- [ ] How long to keep game history?
- [ ] Do users need to view past games?

**Monitoring & Logging**
- [ ] What metrics should be tracked?
- [ ] Error logging strategy?
- [ ] Performance monitoring needs?

**Scalability Horizon**
- [ ] Expected growth trajectory?
- [ ] Plan for 10x growth?
- [ ] Multi-region expansion plans?

**Game Abandonment**
- [ ] How long to wait for all players to disconnect before ending game?
- [ ] Automatic game cleanup for abandoned sessions?

**Maintenance Windows**
- [ ] Acceptable downtime for updates?
- [ ] Need for rolling deployments?

### 4.2 Future Features (Out of Scope for v1)

- Leaderboards and rankings
- Match history and replays
- Friend systems and private rooms
- In-game chat
- Spectator mode
- Tournament support

---

## 5. Success Criteria

**Minimum Viable Product (MVP)**
- [ ] 5 players can join a game session
- [ ] Players can play cards and see updates in real-time
- [ ] Players can reconnect after disconnection and resume
- [ ] Server can restart without losing active games
- [ ] Both authenticated and anonymous users can play
- [ ] Basic rate limiting prevents spam

**Performance Benchmarks**
- [ ] Handle 50 concurrent games without degradation
- [ ] 95% of actions complete within 500ms
- [ ] <1% packet loss on WebSocket connections
- [ ] 99% uptime during normal operation

---

## Notes

This specification will evolve as requirements are refined and technical decisions are made. Each section should be updated as the project progresses.