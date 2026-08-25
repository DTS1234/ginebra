package com.ginebra.game.application;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.*;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.TeamResolver;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
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
    private static final Random FIXED_RANDOM = new Random(42);

    @BeforeEach
    void setUp() {
        gameRepository = new InMemoryGameRepository();
        eventPublisher = new CapturingEventPublisher();

        final var cardRankingService = new CardRankingService();
        gameService = new GameService(
            gameRepository,
            eventPublisher,
            new MoveValidator(cardRankingService),
            new BasaResolver(cardRankingService),
            new TeamResolver(),
            FIXED_CLOCK,
            FIXED_RANDOM
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
