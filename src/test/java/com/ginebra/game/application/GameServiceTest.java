package com.ginebra.game.application;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.*;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class GameServiceTest {

    private InMemoryGameRepository gameRepository;
    private CapturingEventPublisher eventPublisher;
    private GameService gameService;

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-01-15T10:00:00Z"),
        ZoneId.of("UTC")
    );
    /**
     * Fresh per test, not shared. One static Random made every deal depend on how many
     * tests had drawn from it already, so adding a test elsewhere silently re-dealt this
     * one - which is exactly how it broke once.
     */
    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(42);
        gameRepository = new InMemoryGameRepository();
        eventPublisher = new CapturingEventPublisher();

        final var cardRankingService = new CardRankingService();
        gameService = new GameService(
            gameRepository,
            eventPublisher,
            new MoveValidator(cardRankingService),
            new BasaResolver(cardRankingService),
            FIXED_CLOCK,
            random
        );
    }

    @Nested
    class StartGame {

        @Test
        void shouldStartGameSuccessfully() {
            // Arrange
            final var gameId = GameId.generate();
            final var players = generatePlayers();

            // Act
            final var result = gameService.startGame(
                new StartGameUseCase.StartGameCommand(gameId, players)
            );

            // Assert
            assertThat(result).isInstanceOf(StartGameUseCase.StartGameResult.Success.class);
            assertThat(gameRepository.findById(gameId)).isPresent();

            final var game = gameRepository.findById(gameId).orElseThrow();
            assertThat(game.players()).hasSize(5);
            assertThat(game.isInProgress()).isTrue();
            assertThat(game.currentRound()).isPresent();
            assertThat(game.currentRound().get().isWaitingForSoledad()).isTrue();
        }

        @Test
        void shouldRejectDuplicateGame() {
            // Arrange
            final var gameId = GameId.generate();
            final var players = generatePlayers();
            gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));

            // Act
            final var result = gameService.startGame(
                new StartGameUseCase.StartGameCommand(gameId, generatePlayers())
            );

            // Assert
            assertThat(result).isInstanceOf(StartGameUseCase.StartGameResult.GameAlreadyExists.class);
        }
    }

    @Nested
    class SelectTrump {

        @Test
        void shouldSelectTrumpSuccessfully() {
            // Arrange
            final var gameId = GameId.generate();
            final var players = generatePlayers();
            gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));
            passAllSoledadViaService(gameId);

            final var game = gameRepository.findById(gameId).orElseThrow();
            final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();

            // Act
            final var result = gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(gameId, playerWhoGoes, Suit.COPAS)
            );

            // Assert
            assertThat(result).isInstanceOf(SelectTrumpUseCase.SelectTrumpResult.Success.class);

            final var updatedGame = gameRepository.findById(gameId).orElseThrow();
            assertThat(updatedGame.currentRound().orElseThrow().trumpSuit()).contains(Suit.COPAS);
            assertThat(updatedGame.currentRound().orElseThrow().isInProgress()).isTrue();

            assertThat(eventPublisher.events).anyMatch(e -> e instanceof GameEvent.TrumpSelected ts
                && ts.suit() == Suit.COPAS);
        }

        @Test
        void shouldRejectTrumpFromWrongPlayer() {
            // Arrange
            final var gameId = GameId.generate();
            final var players = generatePlayers();
            gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));
            passAllSoledadViaService(gameId);

            final var game = gameRepository.findById(gameId).orElseThrow();
            final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();
            final var wrongPlayer = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();

            // Act
            final var result = gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(gameId, wrongPlayer, Suit.COPAS)
            );

            // Assert
            assertThat(result).isInstanceOf(SelectTrumpUseCase.SelectTrumpResult.NotYourTurn.class);
        }

        @Test
        void shouldReturnGameNotFound() {
            final var result = gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(GameId.generate(), PlayerId.generate(), Suit.COPAS)
            );
            assertThat(result).isInstanceOf(SelectTrumpUseCase.SelectTrumpResult.GameNotFound.class);
        }
    }

    @Nested
    class PlayCard {

        @Test
        void shouldPlayCardSuccessfully() {
            // Arrange
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            final var game = gameRepository.findById(gameId).orElseThrow();
            final var round = game.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var hand = round.getHand(currentPlayer);
            final var card = hand.get(0);

            // Act
            final var result = gameService.playCard(
                new PlayCardUseCase.PlayCardCommand(gameId, currentPlayer, card)
            );

            // Assert
            assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.Success.class);
            assertThat(eventPublisher.events).anyMatch(e -> e instanceof GameEvent.CardPlayed cp
                && cp.playerId().equals(currentPlayer)
                && cp.card().equals(card));
        }

        @Test
        void shouldRejectPlayFromWrongPlayer() {
            // Arrange
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            final var game = gameRepository.findById(gameId).orElseThrow();
            final var round = game.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var wrongPlayer = game.players().stream()
                .filter(p -> !p.equals(currentPlayer))
                .findFirst().orElseThrow();
            final var card = round.getHand(wrongPlayer).get(0);

            // Act
            final var result = gameService.playCard(
                new PlayCardUseCase.PlayCardCommand(gameId, wrongPlayer, card)
            );

            // Assert
            assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.NotYourTurn.class);
        }

        @Test
        void shouldReturnGameNotFound() {
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var result = gameService.playCard(
                new PlayCardUseCase.PlayCardCommand(GameId.generate(), PlayerId.generate(), card)
            );
            assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.GameNotFound.class);
        }

        @Test
        void shouldCompleteBasaAfterFiveCards() {
            // Arrange
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            // Play 5 cards (one full basa)
            for (var i = 0; i < 5; i++) {
                final var game = gameRepository.findById(gameId).orElseThrow();
                final var round = game.currentRound().orElseThrow();
                final var player = round.currentPlayer().orElseThrow();
                final var card = findPlayableCard(round, player);

                final var result = gameService.playCard(
                    new PlayCardUseCase.PlayCardCommand(gameId, player, card)
                );
                assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.Success.class);
            }

            // Assert
            assertThat(eventPublisher.events).anyMatch(e -> e instanceof GameEvent.BasaWon);
        }
    }

    // === Helpers ===

    private void passAllSoledadViaService(GameId gameId) {
        final var game = gameRepository.findById(gameId).orElseThrow();
        final var players = game.currentRound().orElseThrow().playerOrder();
        for (final var player : players) {
            gameService.passSoledad(
                new com.ginebra.game.port.in.SoledadUseCase.PassSoledadCommand(gameId, player)
            );
        }
    }

    private GameId startGameAndSelectTrump(Suit trump) {
        final var gameId = GameId.generate();
        final var players = generatePlayers();
        gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));

        passAllSoledadViaService(gameId);

        final var game = gameRepository.findById(gameId).orElseThrow();
        final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();
        gameService.selectTrump(
            new SelectTrumpUseCase.SelectTrumpCommand(gameId, playerWhoGoes, trump)
        );

        eventPublisher.events.clear();
        return gameId;
    }

    /**
     * The king rules driven through the real service, with the real MoveValidator deciding
     * whether a king was played by choice or dragged out ("et cau el rei").
     */
    @Nested
    class KingDecidesTheSide {

        @Test
        void shouldAnnounceTheSideExactlyOnceWhenTheFirstKingIsPlayed() {
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            playUntilSideDecided(gameId);

            final var pending = eventPublisher.events.stream()
                .filter(GameEvent.KingFellPending.class::isInstance)
                .toList();
            final var decided = eventPublisher.events.stream()
                .filter(GameEvent.SideDecided.class::isInstance)
                .map(GameEvent.SideDecided.class::cast)
                .toList();

            if (!pending.isEmpty()) {
                // The one who goes had their own king dragged out of them, so there is no
                // side yet - the hand is paused on their answer.
                assertThat(pending).hasSize(1);
                assertThat(decided).as("nothing is decided until they answer").isEmpty();
                return;
            }

            assertThat(decided).as("one king decides the round, and only one").hasSize(1);
            final var event = decided.get(0);
            assertThat(event.king().isKing()).isTrue();
            assertThat(event.goingSide()).isNotEmpty();
            assertThat(event.opposingSide()).doesNotContainAnyElementsOf(event.goingSide());
        }

        @Test
        void shouldGiveTheRoundAModeMatchingTheAnnouncement() {
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            playUntilSideDecided(gameId);

            final var event = eventPublisher.events.stream()
                .filter(GameEvent.SideDecided.class::isInstance)
                .map(GameEvent.SideDecided.class::cast)
                .findFirst().orElseThrow();
            final var round = gameRepository.findById(gameId).orElseThrow()
                .currentRound().orElseThrow();

            // A KING_FELL round ends the hand, so the next deal is already under way.
            if (event.mode() != RoundMode.KING_FELL) {
                assertThat(round.mode()).contains(event.mode());
                assertThat(round.goingSide()).isEqualTo(event.goingSide());
            }
            assertThat(event.mode()).isIn(
                RoundMode.HELPED, RoundMode.SELF_KING, RoundMode.KING_FELL
            );
        }

        @Test
        void shouldNotStopTheHandForAHelpersForcedKing() {
            // Only the one who goes is ever asked about their own king; a helper dragged
            // in by a forced king just plays on - the players, 2026-08-27.
            final var gameId = startGameAndSelectTrump(Suit.COPAS);

            playUntilSideDecided(gameId);

            final var decided = eventPublisher.events.stream()
                .filter(GameEvent.SideDecided.class::isInstance)
                .map(GameEvent.SideDecided.class::cast)
                .findFirst();
            final var round = gameRepository.findById(gameId).orElseThrow()
                .currentRound().orElseThrow();

            if (decided.map(e -> e.mode() == RoundMode.HELPED).orElse(false)) {
                assertThat(round.isWaitingForKingChoice()).isFalse();
            }
        }

        /** Plays legal cards until a king turns up and decides the round. */
        private void playUntilSideDecided(GameId gameId) {
            for (var i = 0; i < Round.MAX_BASAS * 5; i++) {
                final var game = gameRepository.findById(gameId).orElseThrow();
                final var round = game.currentRound().orElseThrow();
                if (round.mode().isPresent() || !round.isInProgress()) {
                    return;
                }
                final var current = round.currentPlayer().orElseThrow();
                gameService.playCard(new PlayCardUseCase.PlayCardCommand(
                    gameId, current, findPlayableCard(round, current)
                ));
                if (eventPublisher.events.stream().anyMatch(GameEvent.SideDecided.class::isInstance)) {
                    return;
                }
            }
            throw new IllegalStateException("No king was played in a whole round");
        }
    }

    /**
     * Finds a card that can be legally played by the current player.
     * Tries each card in hand until one is valid.
     */
    private Card findPlayableCard(Round round, PlayerId player) {
        final var hand = round.getHand(player);
        final var trumpSuit = round.trumpSuit().orElseThrow();
        final var basa = round.currentBasa().orElseThrow();
        final var firstCard = basa.cardsPlayed().isEmpty()
            ? Optional.<Card>empty()
            : Optional.of(basa.cardsPlayed().get(0).card());

        final var validator = new MoveValidator(new CardRankingService());
        for (final var card : hand) {
            final var validation = validator.validate(hand, card, trumpSuit, firstCard);
            if (validation instanceof com.ginebra.game.domain.service.MoveValidation.Valid) {
                return card;
            }
        }
        throw new IllegalStateException("No playable card found for player " + player);
    }

    private List<PlayerId> generatePlayers() {
        return List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
    }

    // Test double - captures published events
    /**
     * A round that stops and says nothing is a round nobody can play on: the table sees a
     * hand in progress, no turn, and no way to answer. Every pause has to announce itself.
     *
     * This is the shape of two bugs already - going alone opened a window the client was
     * never shown, and a clean sweep to five paused the hand in silence - so it is pinned
     * here for all of them at once rather than one at a time.
     */
    @Nested
    @DisplayName("Every pause announces itself")
    class PausesAreAnnounced {

        @Test
        void everyWaitingStatusShouldHaveAnEventThatCarriesIt() {
            final var announced = java.util.Map.of(
                RoundStatus.WAITING_FOR_TODO, GameEvent.TodoPending.class,
                RoundStatus.WAITING_FOR_KING_CHOICE, GameEvent.KingFellPending.class,
                RoundStatus.WAITING_FOR_TRUMP, GameEvent.SoledadWindowClosed.class,
                RoundStatus.COMPLETE, GameEvent.RoundEnded.class
            );

            // WAITING_FOR_SOLEDAD is the status a round is dealt into, so it arrives with
            // the state itself rather than as a transition.
            final var pauses = java.util.Arrays.stream(RoundStatus.values())
                .filter(status -> status != RoundStatus.IN_PROGRESS)
                .filter(status -> status != RoundStatus.WAITING_FOR_SOLEDAD)
                .toList();

            assertThat(announced.keySet())
                .as("a status the client is never told about strands whoever is waiting")
                .containsExactlyInAnyOrderElementsOf(pauses);
        }

        @Test
        void shouldAnnounceTheTodoCallWhenTheGoingSideSweepsToFive() {
            // Reported twice from a real table: "cuando yo hago la quinta basa, el juego
            // para i no puedo seguir". The round paused on the todo call and said nothing,
            // so the page went on showing a hand in progress with nobody on turn.
            final var gameId = startGameAndSelectTrump(Suit.COPAS);
            final var goingPlayer = sweepFourBasas(gameId);

            playTheFifthBasaThroughTheService(gameId, goingPlayer);

            final var round = gameRepository.findById(gameId).orElseThrow()
                .currentRound().orElseThrow();
            assertThat(round.isWaitingForTodo())
                .as("five basas and every one of them is what opens the todo call")
                .isTrue();

            final var pending = eventPublisher.events.stream()
                .filter(GameEvent.TodoPending.class::isInstance)
                .map(GameEvent.TodoPending.class::cast)
                .toList();
            assertThat(pending)
                .as("the pause has to reach the table, or nobody can answer it")
                .hasSize(1);
            assertThat(pending.get(0).caller()).isEqualTo(round.todoCaller().orElseThrow());
        }

        /**
         * Hands the first four basas to the side that goes and returns the player who will
         * take the fifth. Done on the aggregate, because a basa's winner is decided by the
         * cards and this test is about what happens after five, not about how they fall.
         *
         * @return the going-side player holding the Espadilla, which wins any basa it is
         *         played into and so settles the fifth without arranging the deal
         */
        private PlayerId sweepFourBasas(GameId gameId) {
            var game = gameRepository.findById(gameId).orElseThrow();
            final var round = game.currentRound().orElseThrow();

            final var espadillaHolder = round.playerOrder().stream()
                .filter(p -> round.getHand(p).stream().anyMatch(Card::isEspadilla))
                .findFirst().orElseThrow();
            final var partner = round.playerOrder().stream()
                .filter(p -> !p.equals(espadillaHolder))
                .findFirst().orElseThrow();

            game = game.setTeams(Teams.of(espadillaHolder, partner, new HashSet<>(round.playerOrder())));

            for (var basa = 0; basa < 4; basa++) {
                game = playFullBasaHoldingBackTheEspadilla(game);
                game = game.completeBasa(espadillaHolder);
            }

            gameRepository.save(game);
            return espadillaHolder;
        }

        /** Five legal cards, none of them the Espadilla - it is wanted for the fifth. */
        private Game playFullBasaHoldingBackTheEspadilla(Game game) {
            for (var seat = 0; seat < Round.PLAYER_COUNT; seat++) {
                final var round = game.currentRound().orElseThrow();
                final var player = round.currentPlayer().orElseThrow();
                final var legal = legalCardsFor(round, player);
                final var card = legal.stream()
                    .filter(c -> !c.isEspadilla())
                    .findFirst()
                    .orElse(legal.get(0));
                game = game.playCard(player, card, FIXED_CLOCK.instant());
            }
            return game;
        }

        /**
         * The fifth basa, played through the service so the real publishing path runs.
         * The Espadilla takes it, which puts the going side on five having won every one.
         */
        private void playTheFifthBasaThroughTheService(GameId gameId, PlayerId espadillaHolder) {
            for (var seat = 0; seat < Round.PLAYER_COUNT; seat++) {
                final var round = gameRepository.findById(gameId).orElseThrow()
                    .currentRound().orElseThrow();
                final var player = round.currentPlayer().orElseThrow();
                final var legal = legalCardsFor(round, player);
                final var card = player.equals(espadillaHolder)
                    ? legal.stream().filter(Card::isEspadilla).findFirst().orElseThrow()
                    : legal.get(0);

                assertThat(gameService.playCard(
                    new PlayCardUseCase.PlayCardCommand(gameId, player, card)
                )).isInstanceOf(PlayCardUseCase.PlayCardResult.Success.class);
            }
        }

        private List<Card> legalCardsFor(Round round, PlayerId player) {
            return new MoveValidator(new CardRankingService()).legalCards(
                round.getHand(player),
                round.trumpSuit().orElseThrow(),
                round.currentBasa()
                    .filter(basa -> !basa.cardsPlayed().isEmpty())
                    .map(basa -> basa.cardsPlayed().get(0).card()),
                new MoveValidator.LeadContext(round.ledSuits(), round.sideDecided())
            );
        }
    }

    private static class CapturingEventPublisher implements GameEventPublisher {

        final List<GameEvent> events = new ArrayList<>();
        final List<PlayerEvent> playerEvents = new ArrayList<>();

        @Override
        public void publishToGame(GameId gameId, GameEvent event) {
            events.add(event);
        }

        @Override
        public void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event) {
            playerEvents.add(new PlayerEvent(playerId, event));
        }

        record PlayerEvent(PlayerId playerId, GameEvent event) {}
    }
}
