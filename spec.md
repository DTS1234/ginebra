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

> **Source.** The rules come from Juan Monjo Soliveres, *«Es joc de ginebra»*, translated in
> [`rules-source.md`](./rules-source.md). Where this section and that document disagree, the
> source wins. [`rules-diff.md`](./rules-diff.md) tracks the differences still outstanding —
> §2.1 to §2.7 have been reconciled with it; what is left is listed in section 3 of
> that document.

### 2.1 Game Overview

**Game Type**: Spanish card trick-taking game for exactly 5 players

**Objective**: The side that "goes" must win **5 basas** (tricks) in the round. The
opposing side wins by holding them to 4 or fewer, which it does the moment it has taken
**4** basas of the 8. There is no draw.

**Deck**: Spanish deck (Baraja Española) - 40 cards
- 4 suits: Copas (Cups), Oros (Coins), Espadas (Swords), Bastos (Clubs)
- Each suit: Ace, 2, 3, 4, 5, 6, 7, Sota (Jack), Horse (Knight), King

### 2.2 Game Setup

**Players**
- Exactly 5 players required to start a game
- Players sit in a fixed circular order
- Turn order proceeds clockwise (to the right)

**The Posso (the pot)**
- Before play begins each player contributes an **equal stake** into the *posso*, which
  sits in the middle of the table
- Rounds are settled **against the posso**: players *collect from* it and *pay into* it.
  They never pay each other, and the two sides of a settlement need not balance — the pot
  absorbs the difference
- If the posso cannot cover a payout it is **topped up in equal parts** by every player
- If a session runs long and the posso has grown large it may be **divided equally**
- Implementation: each player brings 20 coins and antes 5 into the posso at the start; the
  same ante is taken again whenever the pot runs short. The game ends when a player can no
  longer cover what they owe or what they are asked to stake

**Card Distribution**
- Each player receives 8 cards at the start of each round
- Cards are dealt **four at a time**
- 8 cards × 5 players = 40 cards (entire deck is dealt)
- Server shuffles deck before each round
- **Four kings**: a player dealt all four kings collects 4 and the hand ends before anyone
  plays. The deal also pre-empts a Soledad declaration — only that player could go alone

**First Round Start**
- The player holding the Ace of Espadas (Espadilla) starts the first round only
- This establishes the initial turn order
- Subsequent rounds: the player to the right of the previous starting player goes first

### 2.3 Core Gameplay Mechanics

**The Player Who "Goes" (El que Va)**
- The starting player of each round "goes"
- This player selects the trump suit (triumph) for the entire round
- Trump suit determines card rankings and is the "leading color"

**Sides: the king decides them**

The round starts with no sides. The first King played decides them, and which of three
things it decides depends on who played it and whether they had a choice.

| The first King is played by | By choice | Result |
|---|---|---|
| Another player — *posar el rei* | either way | **HELPED**: they aid the one who goes. Two against three |
| The one who goes — *posar-se el rei* | by choice | **SELF_KING**: they play alone. One against four |
| The one who goes — *caure el rei* | forced | **KING_FELL**: the hand ends there, no side ever formed |

- A player holding no King cannot form the partnership
- **Caure el rei**: a King you are forced to play still forms the partnership — *"moltes
  voltes poses el rei sense voler perquè et cau"* — and **costs its owner 1 coin**
- **Es primer rei aida**: the one who goes, holding a bare King (*rei pelat*) that may be
  forced out, may call for a King to be put on them as early as possible

**Playing a Basa (Trick)**
1. Starting player plays any card from their hand
2. Play continues clockwise (to the right)
3. Each player must play one card
4. After all 5 cards are played, the highest card wins the basa
5. Winner collects all 5 cards (the basa counts as 1 toward the 5 needed to win)
6. **The winner of the basa leads the next basa**

**Card Following Rules**

There are two obligations, and which one applies depends on whether the card that opened
the basa was a trump. The Espadilla, the Manilla and the Basto are trumps, so leading any
of them is a trump lead.

*A plain (non-trump) suit is led:*
- **Must follow suit**: If a player holds the suit that was led, they MUST play it
- **Cannot follow suit**: If they hold none of it, they may:
    - Play any other suit
    - **Fallar**: Play a trump card (beats non-trump cards)
    - **Refallar**: Beat another player's trump card with a higher trump
- **Fallar is only open to a player who is void** in the led suit. A player holding the led
  suit may not trump it — the Espadilla and the Basto included

*A trump is led:*
- **Must play a trump** if the player holds one
- **Except** that they may withhold a special card — Espadilla, Manilla or Basto — that
  **outranks the card led**. An ordinary trump that happens to beat the card led carries no
  such privilege
- Which yields the four cases the source states directly:

| Card led | Must play a trump | May withhold |
|----------|-------------------|--------------|
| Espadilla | everyone holding one | nothing |
| Manilla | everyone holding one | Espadilla |
| Basto | everyone holding one | Espadilla, Manilla |
| Any other trump | everyone holding one | Espadilla, Manilla, Basto |

- Withholding is a permission, not a ban: a player may always choose to play the special
  card instead

*Opening a basa, while no King has appeared:*
- The leader **must lead a different suit** from the one they led in the previous basa —
  *"Després has de tirar un altre pal fins que isca o posen rei"*. This is how the King
  gets smoked out
- The obligation **lapses** the moment a King decides the sides
- It **yields to what is possible**: a leader holding nothing outside that suit leads it

*Both cases:*
- **NOT required to "kill"**: Players can play a lower card (don't have to beat previous cards)
- The following rules apply throughout the entire round; the change-of-suit obligation
  applies only before the first King

**Special Cards: Espadilla and Basto**
- **Ace of Espadas (Espadilla)**: Universal high card, doesn't belong to Espadas suit for following purposes
- **Ace of Bastos (Basto)**: Universal high card, doesn't belong to Bastos suit for following purposes
- These two cards beat all other cards in the game
- They are trump cards whatever the trump suit is, and a lead of either makes the basa a
  trump lead (the "leading color")
- Because they are trumps, they are **not** exempt from following a plain suit: a player
  who can still follow the led suit must do so rather than play one. Their privilege is the
  right to be withheld from a trump lead, described above

### 2.4 Card Ranking System

The ranking system depends entirely on which suit is selected as trump. The tables below show the complete card order (ORDRE) for each suit when it "goes" (VA).

**Key Principles:**
- **Espadilla** (Ace of Espadas) is ALWAYS the highest card regardless of trump
- **Manilla** changes based on trump: 7 for Copas/Oros, 2 for Espadas/Bastos
- **Basto** (Ace of Bastos) is ALWAYS the third highest card
- Trump suit cards beat all non-trump cards
- Within non-trump suits: King > Horse > Sota > Ace (if present) > the low cards
- The low cards run in opposite directions by suit, trump or not: **Copas and Oros** rank 2 > 3 > 4 > 5 > 6 > 7, **Espadas and Bastos** rank 7 > 6 > 5 > 4 > 3 > 2

**Note on Manilla**: When a card becomes the Manilla, it occupies position 2 in the ranking and doesn't appear again in its normal position.

**Note on the Ace names**: the Ace of Oros is the ***Rovell***, the Ace of Copes the
***Carabassa***.

**Note on the Ace's two positions** (confirmed by Tàrbena players, 2026-08-26): the Ace
moves depending on whether its suit is trump.

| | Order, strongest first |
|---|---|
| Oros **not** trump | Rey, Caballo, Sota, **Rovell**, 2, 3, 4, 5, 6, 7 |
| Oros **as** trump | *(Espadilla, Basto above)* 7 **manilla**, **Rovell**, Rey, Caballo, Sota, 2, 3, 4, 5, 6 |

The same holds for Copas with the *Carabassa*. Espadas and Bastos have no ordinary Ace —
theirs are the Espadilla and the Basto — and their low cards run 7 down to 2 instead.

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

---

#### When OROS "Goes" (Trump)

| Rank | COPAS | **OROS (Trump)** | ESPADAS | BASTOS |
|------|-------|------------------|---------|--------|
| 1 | Rei de Copes | **As d'Espases (espadilla)** | Rei d'Espases | Rei de Bastos |
| 2 | Cavall de Copes | **7 d'Oros (manilla)** | Cavall d'Espases | Cavall de Bastos |
| 3 | Sota de Copes | **As de Bastos (Basto)** | Sota d'Espases | Sota de Bastos |
| 4 | As de Copes (Carabassa) | **As d'Oros (Rovell)** | 7 d'Espases | 7 de Bastos |
| 5 | 2 de Copes | **Rei d'Oros** | 6 d'Espases | 6 de Bastos |
| 6 | 3 de Copes | **Cavall d'Oros** | 5 d'Espases | 5 de Bastos |
| 7 | 4 de Copes | **Sota d'Oros** | 4 d'Espases | 4 de Bastos |
| 8 | 5 de Copes | **2 d'Oros** | 3 d'Espases | 3 de Bastos |
| 9 | 6 de Copes | **3 d'Oros** | 2 d'Espases | 2 de Bastos |
| 10 | 7 de Copes | **4 d'Oros** | | |
| 11 | | **5 d'Oros** | | |
| 12 | | **6 d'Oros** | | |

---

#### When ESPASES "Goes" (Trump)

| Rank | COPAS | OROS | **ESPASES (Trump)** | BASTOS |
|------|-------|------|---------------------|--------|
| 1 | Rei de Copes | Rei d'Oros | **As d'Espases (espadilla)** | Rei de Bastos |
| 2 | Cavall de Copes | Cavall d'Oros | **2 d'Espases (manilla)** | Cavall de Bastos |
| 3 | Sota de Copes | Sota d'Oros | **As de Bastos (Basto)** | Sota de Bastos |
| 4 | As de Copes (Carabassa) | As d'Oros (Rovell) | **Rei d'Espases** | 7 de Bastos |
| 5 | 2 de Copes | 2 d'Oros | **Cavall d'Espases** | 6 de Bastos |
| 6 | 3 de Copes | 3 d'Oros | **Sota d'Espases** | 5 de Bastos |
| 7 | 4 de Copes | 4 d'Oros | **7 d'Espases** | 4 de Bastos |
| 8 | 5 de Copes | 5 d'Oros | **6 d'Espases** | 3 de Bastos |
| 9 | 6 de Copes | 6 d'Oros | **5 d'Espases** | 2 de Bastos |
| 10 | 7 de Copes | 7 d'Oros | **4 d'Espases** | | |
| 11 | | | **3 d'Espases** | | |

---

#### When BASTOS "Goes" (Trump)

| Rank | COPAS | OROS | ESPADAS | **BASTOS (Trump)** |
|------|-------|------|---------|-------------------|
| 1 | Rei de Copes | Rei d'Oros | Rei d'Espases | **As d'Espases (espadilla)** |
| 2 | Cavall de Copes | Cavall d'Oros | Cavall d'Espases | **2 de Bastos (manilla)** |
| 3 | Sota de Copes | Sota d'Oros | Sota d'Espases | **As de Bastos (Basto)** |
| 4 | As de Copes (Carabassa) | As d'Oros (Rovell) | 7 d'Espases | **Rei de Bastos** |
| 5 | 2 de Copes | 2 d'Oros | 6 d'Espases | **Cavall de Bastos** |
| 6 | 3 de Copes | 3 d'Oros | 5 d'Espases | **Sota de Bastos** |
| 7 | 4 de Copes | 4 d'Oros | 4 d'Espases | **7 de Bastos** |
| 8 | 5 de Copes | 5 d'Oros | 3 d'Espases | **6 de Bastos** |
| 9 | 6 de Copes | 6 d'Oros | 2 d'Espases | **5 de Bastos** |
| 10 | 7 de Copes | 7 d'Oros | | **4 de Bastos** |
| 11 | | | | **3 de Bastos** |

---

**Important Notes:**
1. The three special cards (Espadilla, Manilla, Basto) always occupy the top 3 positions
2. When Espadas or Bastos is trump, their respective 2 becomes the Manilla and doesn't reappear at position 12
3. Non-trump suits have fewer cards in ranking (9 cards each) since their Aces are special
4. The trump suit has all its cards ranked (13 positions for Copas/Oros, 11 for Espadas/Bastos after accounting for special cards)

### 2.5 Special Game Mode: Soledad (*anar a soles*)

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

The round is decided by whichever of these comes first:

- The **going side reaches 5 basas** — unless *todo* is still reachable, in which case play
  continues (see below)
- The **opposing side reaches 4 basas**, which puts 5 out of the going side's reach
- All 8 basas are played
- The **mà's own King is forced out** (*caure el rei*), which ends the hand where it stands
- A **four-king deal**, which ends the hand before anyone plays

**Fer todo**: winning *all eight* basas. The source has the going side call it on reaching
five, and play on. Because a missed *todo* costs nothing beyond the point itself, the
engine treats reaching five with a clean sweep as the call: the round continues while every
basa so far belongs to the going side, and settles the moment one does not.

**Fer primeres**: winning the **first four basas in a row**. Worth a point to the going side.

**Settlement**

All figures are collected from, or paid into, the **posso**. Each is one **base** plus a
set of **+1 increments**, and the same figure is collected on a win and paid on a loss.

| Base — mutually exclusive | |
|---|---|
| Helped: a King was put on you (each of the pair) | 2 |
| You put your own King (*posar-se el rei*) | 4 |
| You went alone (*anar a soles*) | 5 |
| You were dealt the four kings | 4 |

| Increment | |
|---|---|
| *Primeres* — the first four basas in a row | +1 |
| *Todo* — all eight basas (contains primeres) | +1 |
| *Dengue* — Espadilla + Basto in your dealt hand | +1 |
| *Estutxe* — Espadilla + Manilla + Basto, on top of the dengue | +1 |

Two items sit outside the ladder:

- **The dengue is always collected** — *"El dengue sempre es cobra"* — win or lose, and on
  either side. It is the one item that never appears on the paying side.
- **A forced King costs its owner 1** (*caure el rei*), whoever they are.

**The opposing side** collects a flat **1** when the going side fails, or **2** if the going
side was held under four basas. It pays nothing when the going side wins — the source lists
no charge for losing defenders, and the pot covers the difference.

Sanity check against the source's own maximum: `5 (alone) + 4 (four kings) + 1 (primeres) +
2 (dengue and estutxe) + 1 (todo) = 13`.

*Two readings the source leaves open, resolved here and recorded in `rules-diff.md`: the
printed row "Per guanyar i tindre l'estutxe, 3 cadegú (si n té el dengue, 4)" is taken at
its parenthetical, since the estutxe contains the dengue and so always scores both; and a
losing side's payment is raised by its **own** primeres and estutxe.*

### 2.7 Complete Game Flow

```
┌─────────────────────────────────────┐
│     NEW ROUND SETUP                 │
├─────────────────────────────────────┤
│ 1. Deal 8 cards, four at a time     │
│ 2. Four kings in one hand? Settle   │
│    and re-deal                      │
│ 3. Determine the ma:                │
│    - First round: Espadilla holder  │
│    - Subsequent: Clockwise rotation │
│ 4. Soledad declaration (optional)   │
│ 5. Trump chooser (the ma, or the    │
│    Soledad declarer) selects trump  │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│     PLAY BASAS (Up to 8 tricks)     │
├─────────────────────────────────────┤
│ For each basa:                      │
│ 1. Starting player plays card       │
│ 2. Play continues clockwise         │
│ 3. Follow suit; a trump lead compels│
│    a trump (see 2.3)                │
│ 4. Highest card wins basa           │
│ 5. First King decides the sides     │
│ 6. Next basa: led by the winner     │
│                                     │
│ Ends when the going side makes 5,   │
│ the other side takes 4, or the ma's │
│ King is forced out                  │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│     ROUND END & SCORING             │
├─────────────────────────────────────┤
│ 1. Determine the outcome            │
│ 2. Price it: base + increments      │
│ 3. Settle against the posso, topping│
│    it up if it cannot cover         │
│ 4. Update balances and the posso    │
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

**Answered by the source** (`rules-source.md` §3, §4.1, §5):
- [x] *What happens when a player runs out of coins?* — there are no player-to-player
      balances. If the **posso** runs dry it is topped up in equal parts by agreement.
- [x] *Is there an overall game winner?* — no. Play continues by agreement; when it ends,
      or the posso has grown too large, it is divided equally.
- [x] *Draw condition* — there is none. A 4-4 finish is the going side failing.
- [x] *Initial pot contribution* — whatever the table agrees, equal for everyone, and
      changeable mid-session by common agreement.

**Still open — the source does not settle these:**
- [ ] Exact timing of Soledad declaration - before or after trump selection?
- [ ] Can multiple players declare Soledad? If yes, how to resolve? (The source settles only
      the four-king case: that deal pre-empts any declaration.)
- [ ] What happens if the trump chooser disconnects before choosing?
- [ ] Can players join/leave between rounds, or must the same 5 play the entire game?
- [ ] Maximum game duration or number of rounds?
- [ ] What a *missed* todo costs, if anything, once it has been called.
- [ ] Whether a four-king holder may decline the 4 and play the hand out alone instead —
      the source's 13-coin maximum implies they can, but §4.8 says the hand ends.
- [ ] Whether the Ace outranks or falls below the Sota in a **non-trump** suit (D-6).
- [ ] Whether the leader must change suit each basa until a King appears (D-17).

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