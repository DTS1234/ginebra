package com.ginebra.connection.application;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        final var disconnected = tracker.playerDisconnected(sessionId, Instant.now());

        // Assert
        assertThat(disconnected).isPresent();
        assertThat(disconnected.get().playerId()).isEqualTo(playerId);
        assertThat(tracker.isConnected(playerId)).isFalse();
        assertThat(tracker.getConnectedPlayers(gameId)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownDisconnection() {
        final var disconnected = tracker.playerDisconnected("unknown-session", Instant.now());
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

    @Nested
    @DisplayName("Somebody who has gone")
    class Absences {

        private static final Instant LEFT_AT = Instant.parse("2026-01-15T10:00:00Z");
        private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

        @Test
        void shouldBeCountedFromWhenTheirConnectionDied() {
            final var playerId = PlayerId.generate();
            final var gameId = GameId.generate();
            tracker.playerConnected(playerId, gameId, "s1", LEFT_AT.minusSeconds(60));

            tracker.playerDisconnected("s1", LEFT_AT);

            assertThat(tracker.absencesLongerThan(FIVE_MINUTES, LEFT_AT.plus(FIVE_MINUTES)))
                .extracting(ConnectionTracker.Absence::playerId, ConnectionTracker.Absence::gameId)
                .containsExactly(org.assertj.core.api.Assertions.tuple(playerId, gameId));
        }

        @Test
        void shouldNotCountUntilTheirTimeIsUp() {
            tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", LEFT_AT);
            tracker.playerDisconnected("s1", LEFT_AT);

            assertThat(tracker.absencesLongerThan(FIVE_MINUTES, LEFT_AT.plus(FIVE_MINUTES).minusSeconds(1)))
                .as("a second short of five minutes is not five minutes")
                .isEmpty();
        }

        @Test
        void shouldStopCountingTheMomentTheyComeBack() {
            final var playerId = PlayerId.generate();
            final var gameId = GameId.generate();
            tracker.playerConnected(playerId, gameId, "s1", LEFT_AT);
            tracker.playerDisconnected("s1", LEFT_AT);

            tracker.playerConnected(playerId, gameId, "s2", LEFT_AT.plusSeconds(30));

            assertThat(tracker.absencesLongerThan(FIVE_MINUTES, LEFT_AT.plusSeconds(3600)))
                .as("they are sitting there; nobody is taking their seat")
                .isEmpty();
        }

        @Test
        void shouldNotBeStartedByAnOldSessionDyingAfterTheyReconnected() {
            // The old session's DISCONNECT can arrive after the new one's CONNECT. Taken
            // at face value it would mark a player absent while they are looking at the
            // table.
            final var playerId = PlayerId.generate();
            final var gameId = GameId.generate();
            tracker.playerConnected(playerId, gameId, "s-old", LEFT_AT);
            tracker.playerConnected(playerId, gameId, "s-new", LEFT_AT.plusSeconds(1));

            tracker.playerDisconnected("s-old", LEFT_AT.plusSeconds(2));

            assertThat(tracker.isConnected(playerId)).isTrue();
            assertThat(tracker.absencesLongerThan(FIVE_MINUTES, LEFT_AT.plusSeconds(3600))).isEmpty();
        }

        @Test
        void shouldBeForgettableOnceItHasBeenDealtWith() {
            final var playerId = PlayerId.generate();
            tracker.playerConnected(playerId, GameId.generate(), "s1", LEFT_AT);
            tracker.playerDisconnected("s1", LEFT_AT);

            tracker.forgetAbsence(playerId);

            assertThat(tracker.absencesLongerThan(FIVE_MINUTES, LEFT_AT.plusSeconds(3600)))
                .as("their seat has been handed over; there is nothing left to notice")
                .isEmpty();
        }
    }

    @Test
    void shouldClear() {
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s1", Instant.now());
        tracker.playerConnected(PlayerId.generate(), GameId.generate(), "s2", Instant.now());

        tracker.clear();

        assertThat(tracker.getConnectedPlayers(GameId.generate())).isEmpty();
    }
}
