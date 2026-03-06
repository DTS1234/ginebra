package com.ginebra.connection.application;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTrackerTest {

    private ConnectionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ConnectionTracker();
    }

    @Test
    void shouldTrackPlayerConnection() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var gameId = GameId.generate();
        final var sessionId = "session-1";

        // Act
        tracker.playerConnected(playerId, gameId, sessionId, Instant.now());

        // Assert
        assertThat(tracker.isConnected(playerId)).isTrue();
        assertThat(tracker.getConnection(playerId)).isPresent();
        assertThat(tracker.getConnection(playerId).get().gameId()).isEqualTo(gameId);
    }

    @Test
    void shouldReturnConnectedPlayersForGame() {
        // Arrange
        final var gameId = GameId.generate();
        final var p1 = PlayerId.generate();
        final var p2 = PlayerId.generate();
        final var p3 = PlayerId.generate();
        final var otherGameId = GameId.generate();
        final var p4 = PlayerId.generate();

        tracker.playerConnected(p1, gameId, "s1", Instant.now());
        tracker.playerConnected(p2, gameId, "s2", Instant.now());
        tracker.playerConnected(p3, gameId, "s3", Instant.now());
        tracker.playerConnected(p4, otherGameId, "s4", Instant.now());

        // Act
        final var connectedPlayers = tracker.getConnectedPlayers(gameId);

        // Assert
        assertThat(connectedPlayers).containsExactlyInAnyOrder(p1, p2, p3);
    }

    @Test
    void shouldHandlePlayerDisconnection() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var gameId = GameId.generate();
        final var sessionId = "session-1";
        tracker.playerConnected(playerId, gameId, sessionId, Instant.now());

        // Act
        final var disconnected = tracker.playerDisconnected(sessionId);

        // Assert
        assertThat(disconnected).isPresent();
        assertThat(disconnected.get().playerId()).isEqualTo(playerId);
        assertThat(tracker.isConnected(playerId)).isFalse();
        assertThat(tracker.getConnectedPlayers(gameId)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownDisconnection() {
        final var disconnected = tracker.playerDisconnected("unknown-session");
        assertThat(disconnected).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnconnectedPlayer() {
        assertThat(tracker.isConnected(PlayerId.generate())).isFalse();
        assertThat(tracker.getConnection(PlayerId.generate())).isEmpty();
    }

    @Test
    void shouldHandleReconnection() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var gameId = GameId.generate();
        tracker.playerConnected(playerId, gameId, "session-old", Instant.now());

        // Reconnect with new session
        tracker.playerConnected(playerId, gameId, "session-new", Instant.now());

        // Assert
        assertThat(tracker.isConnected(playerId)).isTrue();
        assertThat(tracker.getConnection(playerId).get().sessionId()).isEqualTo("session-new");
    }

    @Test
    void shouldClear() {
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", Instant.now());
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s2", Instant.now());

        tracker.clear();

        assertThat(tracker.getConnectedPlayers(GameId.generate())).isEmpty();
    }
}
