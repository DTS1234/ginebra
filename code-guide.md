# Ginebra Online - Code Guide

## Technology Stack

| Component | Choice |
|-----------|--------|
| Language | Java 25 |
| Build | Gradle Kotlin DSL |
| Framework | Spring Boot |
| Testing | JUnit 5 + AssertJ + WireMock |

---

## 1. Code Style

### 1.1 Variables: Prefer `final var`

Always use `final var` for local variables. Explicit types only when necessary for clarity.

```java
// ✓ Good
final var player = playerRepository.findById(playerId);
final var cards = new ArrayList<Card>();
final var count = players.size();

// ✗ Avoid
Player player = playerRepository.findById(playerId);
var player = playerRepository.findById(playerId);  // missing final
```

### 1.2 Fields: Explicit Types with `final`

Class fields use explicit types for clarity.

```java
// ✓ Good
private final PlayerRepository playerRepository;
private final Clock clock;

// ✗ Avoid
private var playerRepository;  // var not allowed for fields anyway
```

### 1.3 Constants

```java
// ✓ Good
private static final Duration SOLEDAD_TIMEOUT = Duration.ofMinutes(2);
private static final int MAX_PLAYERS = 5;
```

---

## 2. Null Handling

### 2.1 Use `Optional` Instead of `@Nullable`

Return `Optional` for values that may be absent. Never return `null`.

```java
// ✓ Good
public Optional<Player> findById(PlayerId id) {
    return Optional.ofNullable(players.get(id));
}

public Optional<Suit> getTrumpSuit() {
    return Optional.ofNullable(this.trumpSuit);
}

// ✗ Avoid
@Nullable
public Player findById(PlayerId id) {
    return players.get(id);
}
```

### 2.2 Consuming Optionals

```java
// ✓ Good - explicit handling
final var player = playerRepository.findById(id)
    .orElseThrow(() -> new PlayerNotFoundException(id));

final var displayName = player.getDisplayName()
    .orElse("Anonymous");

// ✓ Good - conditional logic
playerRepository.findById(id).ifPresent(player -> {
    notifyPlayer(player, event);
});

// ✗ Avoid - isPresent + get
if (optional.isPresent()) {
    doSomething(optional.get());
}
```

### 2.3 Constructor Validation

Validate all parameters in constructors. Fail fast on invalid state.

```java
// ✓ Good
public record Game(
    GameId id,
    List<Player> players,
    Suit trumpSuit  // nullable - not yet selected
) {
    public Game {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(players, "players must not be null");
        
        if (players.size() != 5) {
            throw new IllegalArgumentException("Game requires exactly 5 players");
        }
        
        // trumpSuit can be null (not selected yet) - no validation
        
        // Defensive copy for mutable collections
        players = List.copyOf(players);
    }
}
```

```java
// ✓ Good - class constructor
public class GameService {
    
    private final GameRepository gameRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    
    public GameService(
        GameRepository gameRepository,
        EventPublisher eventPublisher,
        Clock clock
    ) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }
}
```

### 2.4 Nullable Fields in Domain Objects

For fields that are legitimately nullable (e.g., `trumpSuit` before selection), expose via `Optional` getter:

```java
public class Round {
    
    private final Suit trumpSuit;  // null until selected
    
    public Optional<Suit> getTrumpSuit() {
        return Optional.ofNullable(trumpSuit);
    }
}
```

---

## 3. Testing

### 3.1 No Mockito

Use real implementations or hand-written test doubles. This encourages better design.

```java
// ✓ Good - in-memory implementation
public class InMemoryGameRepository implements GameRepository {
    
    private final Map<GameId, Game> games = new ConcurrentHashMap<>();
    
    @Override
    public void save(Game game) {
        games.put(game.id(), game);
    }
    
    @Override
    public Optional<Game> findById(GameId id) {
        return Optional.ofNullable(games.get(id));
    }
    
    // Test helper
    public void clear() {
        games.clear();
    }
}

// ✗ Avoid - Mockito
@Mock
private GameRepository gameRepository;

when(gameRepository.findById(any())).thenReturn(Optional.of(game));
```

### 3.2 AssertJ for Assertions

Use AssertJ's fluent API for all assertions.

```java
// ✓ Good - AssertJ
assertThat(game.getPlayers()).hasSize(5);
assertThat(game.getTrumpSuit()).isEmpty();
assertThat(result).isInstanceOf(InvalidMoveError.class);
assertThat(cards)
    .extracting(Card::suit)
    .containsOnly(Suit.COPAS);

// ✗ Avoid - JUnit assertions
assertEquals(5, game.getPlayers().size());
assertNull(game.getTrumpSuit());
assertTrue(result instanceof InvalidMoveError);
```

### 3.3 Parameterized Tests

Use parameterized tests for rule validation and multiple scenarios.

```java
// ✓ Good - parameterized test for card ranking
@ParameterizedTest
@MethodSource("cardRankingScenarios")
void shouldRankCardsCorrectly(Suit trump, Card card1, Card card2, Card expectedWinner) {
    final var ranking = new CardRankingService();
    
    final var winner = ranking.higher(trump, card1, card2);
    
    assertThat(winner).isEqualTo(expectedWinner);
}

static Stream<Arguments> cardRankingScenarios() {
    return Stream.of(
        // Trump COPAS: Espadilla beats everything
        Arguments.of(COPAS, ESPADILLA, card(COPAS, REY), ESPADILLA),
        // Trump COPAS: Manilla (7 of COPAS) beats Basto
        Arguments.of(COPAS, card(COPAS, SEVEN), BASTO, card(COPAS, SEVEN)),
        // Trump COPAS: Trump beats non-trump
        Arguments.of(COPAS, card(COPAS, TWO), card(OROS, REY), card(COPAS, TWO)),
        // Trump ESPADAS: 2 is Manilla
        Arguments.of(ESPADAS, card(ESPADAS, TWO), card(ESPADAS, REY), card(ESPADAS, TWO))
    );
}
```

```java
// ✓ Good - parameterized test for move validation
@ParameterizedTest
@CsvSource({
    "BASTOS, true,  BASTOS, true",   // has suit, plays suit = valid
    "BASTOS, true,  COPAS,  false",  // has suit, plays other = invalid
    "BASTOS, false, COPAS,  true",   // no suit, plays other = valid (fallar)
    "BASTOS, false, BASTOS, false"   // no suit, plays suit = impossible hand state
})
void shouldValidateFollowSuit(Suit ledSuit, boolean hasSuit, Suit playedSuit, boolean expectedValid) {
    // ... test implementation
}
```

```java
// ✓ Good - parameterized test with enum source
@ParameterizedTest
@EnumSource(Suit.class)
void shouldDetermineManillaForEachTrump(Suit trump) {
    final var ranking = new CardRankingService();
    
    final var manilla = ranking.getManilla(trump);
    
    assertThat(manilla.suit()).isEqualTo(trump);
    if (trump == COPAS || trump == OROS) {
        assertThat(manilla.rank()).isEqualTo(SEVEN);
    } else {
        assertThat(manilla.rank()).isEqualTo(TWO);
    }
}
```

### 3.4 WireMock for HTTP Endpoints

Use WireMock for testing HTTP integrations and REST endpoints.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class ExternalServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldHandleExternalServiceTimeout() {
        // Arrange
        stubFor(get(urlEqualTo("/external/api"))
            .willReturn(aResponse()
                .withFixedDelay(5000)
                .withStatus(200)));
        
        // Act
        final var response = restTemplate.getForEntity("/our-endpoint", String.class);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }
}
```

### 3.5 Test Structure

Follow Arrange-Act-Assert pattern with clear separation.

```java
@Test
void shouldRejectCardWhenPlayerMustFollowSuit() {
    // Arrange
    final var hand = List.of(
        card(COPAS, REY),
        card(COPAS, HORSE),
        card(OROS, THREE)
    );
    final var validator = new MoveValidator();
    final var ledSuit = COPAS;
    final var attemptedCard = card(OROS, THREE);
    
    // Act
    final var result = validator.validate(hand, ledSuit, attemptedCard);
    
    // Assert
    assertThat(result.isValid()).isFalse();
    assertThat(result.errorCode()).isEqualTo("MUST_FOLLOW_SUIT");
}
```

### 3.6 Test Naming

Use descriptive names that explain the scenario.

```java
// ✓ Good - descriptive names
@Test
void shouldAutoPassPlayerAfterTwoMinuteTimeout() { }

@Test
void shouldRejectCardPlayWhenNotPlayersTurn() { }

@Test
void shouldTransitionToWaitingForTrumpWhenAllPlayersPassSoledad() { }

// ✗ Avoid - vague names
@Test
void testTimeout() { }

@Test
void testValidation() { }
```

---

## 4. Domain Modeling

### 4.1 Prefer Records for Value Objects

```java
// ✓ Good
public record Card(Suit suit, Rank rank) {
    public Card {
        Objects.requireNonNull(suit, "suit must not be null");
        Objects.requireNonNull(rank, "rank must not be null");
    }
    
    public static Card espadilla() {
        return new Card(ESPADAS, ACE);
    }
    
    public static Card basto() {
        return new Card(BASTOS, ACE);
    }
}

public record PlayerId(UUID value) {
    public PlayerId {
        Objects.requireNonNull(value, "value must not be null");
    }
    
    public static PlayerId generate() {
        return new PlayerId(UUID.randomUUID());
    }
}
```

### 4.2 Encapsulate Domain Logic

Keep business logic in domain objects, not services.

```java
// ✓ Good - logic in domain object
public class Round {
    
    public boolean canPlayCard(PlayerId player, Card card) {
        if (!isCurrentPlayer(player)) {
            return false;
        }
        final var hand = hands.get(player);
        final var ledSuit = currentBasa.getLedSuit();
        
        return ledSuit
            .map(suit -> !hasNonSpecialCardsOfSuit(hand, suit) || card.suit() == suit || card.isSpecial())
            .orElse(true);  // First card of basa - anything goes
    }
}

// ✗ Avoid - logic in service
public class GameService {
    
    public boolean canPlayCard(Game game, PlayerId player, Card card) {
        // All the logic here instead of in domain
    }
}
```

---

## 5. Spring Conventions

### 5.1 Constructor Injection

Always use constructor injection. No `@Autowired` on fields.

```java
// ✓ Good
@Service
public class GameService {
    
    private final GameRepository gameRepository;
    private final EventPublisher eventPublisher;
    
    public GameService(GameRepository gameRepository, EventPublisher eventPublisher) {
        this.gameRepository = Objects.requireNonNull(gameRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }
}

// ✗ Avoid
@Service
public class GameService {
    
    @Autowired
    private GameRepository gameRepository;
}
```

### 5.2 Configuration Properties

Use records for configuration.

```java
@ConfigurationProperties(prefix = "ginebra.game")
public record GameProperties(
    Duration soledadTimeout,
    Duration disconnectTimeout,
    int initialCoins
) {
    public GameProperties {
        if (soledadTimeout == null) soledadTimeout = Duration.ofMinutes(2);
        if (disconnectTimeout == null) disconnectTimeout = Duration.ofMinutes(5);
        if (initialCoins <= 0) initialCoins = 20;
    }
}
```

---

## 6. Error Handling

### 6.1 Domain Errors as Types

Use sealed interfaces for domain errors.

```java
public sealed interface MoveResult {
    record Success(GameState newState) implements MoveResult {}
    record InvalidMove(String code, String message) implements MoveResult {}
}

// Usage
public MoveResult playCard(PlayerId player, Card card) {
    if (!isCurrentPlayer(player)) {
        return new MoveResult.InvalidMove("NOT_YOUR_TURN", "It's not your turn");
    }
    // ...
    return new MoveResult.Success(newState);
}

// Handling
switch (result) {
    case MoveResult.Success s -> broadcast(s.newState());
    case MoveResult.InvalidMove e -> sendError(player, e.code(), e.message());
}
```

---

## 7. Project Structure

```
src/
├── main/java/com/ginebra/
│   ├── game/
│   │   ├── domain/           # Entities, value objects, domain services
│   │   ├── application/      # Use cases, application services
│   │   ├── port/
│   │   │   ├── in/          # Driving ports (use case interfaces)
│   │   │   └── out/         # Driven ports (repository interfaces)
│   │   └── adapter/
│   │       ├── in/          # Controllers, WebSocket handlers
│   │       └── out/         # Repository implementations
│   └── ...
└── test/java/com/ginebra/
    ├── game/
    │   ├── domain/           # Pure unit tests
    │   ├── application/      # Tests with in-memory adapters
    │   └── adapter/
    │       └── out/         # Integration tests with Testcontainers
    └── support/              # Test utilities, builders, in-memory implementations
```

---

## 8. Quick Reference

| Situation | Do This |
|-----------|---------|
| Local variable | `final var x = ...` |
| May return nothing | Return `Optional<T>` |
| Constructor param | `Objects.requireNonNull(param, "message")` |
| Nullable field getter | Return `Optional.ofNullable(field)` |
| Multiple test cases | `@ParameterizedTest` |
| Assertions | AssertJ: `assertThat(x).isEqualTo(y)` |
| Mock dependencies | Write in-memory implementation |
| HTTP testing | WireMock |
| Value object | `record` with validation in compact constructor |
| Error modeling | Sealed interface with record variants |