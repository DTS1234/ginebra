package com.ginebra.game.adapter.in.dto;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Game;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateMapperTest {

    private GameStateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GameStateMapper();
    }

    @Test
    void shouldMapGameStateForPlayer() {
        // Arrange
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        final var game = Game.start(GameId.generate(), players, new Random(42));
        final var targetPlayer = players.get(0);

        // Act
        final var payload = mapper.toGameState(game, targetPlayer);

        // Assert
        assertThat(payload.gameId()).isEqualTo(game.gameId().value().toString());
        assertThat(payload.status()).isEqualTo("IN_PROGRESS");
        assertThat(payload.players()).hasSize(5);
        assertThat(payload.coinBalances()).hasSize(5);
        assertThat(payload.currentRound()).isNotNull();
        assertThat(payload.currentRound().status()).isEqualTo("WAITING_FOR_TRUMP");
    }

    @Test
    void shouldIncludeOnlyRequestingPlayersHand() {
        // Arrange
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        final var game = Game.start(GameId.generate(), players, new Random(42));

        // Act - get state for player 0 and player 1
        final var stateForP0 = mapper.toGameState(game, players.get(0));
        final var stateForP1 = mapper.toGameState(game, players.get(1));

        // Assert - each player gets their own hand
        assertThat(stateForP0.currentRound().yourHand()).hasSize(8);
        assertThat(stateForP1.currentRound().yourHand()).hasSize(8);

        // Hands should differ (different cards)
        assertThat(stateForP0.currentRound().yourHand())
            .isNotEqualTo(stateForP1.currentRound().yourHand());
    }

    @Test
    void shouldMapSeatPositions() {
        // Arrange
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        final var game = Game.start(GameId.generate(), players, new Random(42));

        // Act
        final var payload = mapper.toGameState(game, players.get(0));

        // Assert
        for (var i = 0; i < 5; i++) {
            assertThat(payload.players().get(i).seatPosition()).isEqualTo(i);
            assertThat(payload.players().get(i).id()).isEqualTo(players.get(i).value().toString());
        }
    }

    @Test
    void shouldMapTrumpSelectedEvent() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var nextTurn = PlayerId.generate();
        final var event = new GameEvent.TrumpSelected(playerId, Suit.COPAS, nextTurn);

        // Act
        final var message = mapper.toServerMessage(event);

        // Assert
        assertThat(message.type()).isEqualTo("TRUMP_SELECTED");
        assertThat(message.payload()).isNotNull();
    }

    @Test
    void shouldMapCardPlayedEvent() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var card = Card.espadilla();
        final var event = new GameEvent.CardPlayed(playerId, card, PlayerId.generate());

        // Act
        final var message = mapper.toServerMessage(event);

        // Assert
        assertThat(message.type()).isEqualTo("CARD_PLAYED");
    }

    @Test
    void shouldMapGameEndedEvent() {
        // Arrange
        final var event = new GameEvent.GameEnded("PLAYER_BANKRUPT", java.util.Map.of());

        // Act
        final var message = mapper.toServerMessage(event);

        // Assert
        assertThat(message.type()).isEqualTo("GAME_ENDED");
    }

    @Test
    void shouldMapErrorEvent() {
        // Arrange
        final var event = new GameEvent.GameError("NOT_YOUR_TURN", "It's not your turn");

        // Act
        final var message = mapper.toServerMessage(event);

        // Assert
        assertThat(message.type()).isEqualTo("ERROR");
    }

    @Test
    void shouldMapGameStateAfterTrumpSelection() {
        // Arrange
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        final var game = Game.start(GameId.generate(), players, new Random(42));
        final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();
        final var gameAfterTrump = game.selectTrump(Suit.OROS);

        // Act
        final var payload = mapper.toGameState(gameAfterTrump, players.get(0));

        // Assert
        assertThat(payload.currentRound().status()).isEqualTo("IN_PROGRESS");
        assertThat(payload.currentRound().trumpSuit()).isEqualTo("OROS");
        assertThat(payload.currentRound().playerWhoGoes()).isEqualTo(playerWhoGoes.value().toString());
        assertThat(payload.currentRound().currentBasa()).isNotNull();
        assertThat(payload.currentRound().currentBasa().basaNumber()).isEqualTo(1);
    }
}
