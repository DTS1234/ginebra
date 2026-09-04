package com.ginebra.connection.application;

import com.ginebra.connection.domain.PlayerConnection;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks active WebSocket connections per player and per game, and how long the ones that
 * went away have been gone.
 *
 * Thread-safe for concurrent connection/disconnection events.
 */
@Component
public class ConnectionTracker {

    private final ConcurrentHashMap<String, PlayerConnection> connectionsBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PlayerId, PlayerConnection> connectionsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PlayerId, Absence> absences = new ConcurrentHashMap<>();

    /** Somebody who was in a game and is not any more, and since when. */
    public record Absence(PlayerId playerId, GameId gameId, Instant since) {
        public Absence {
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(since, "since must not be null");
        }
    }

    public void playerConnected(PlayerId playerId, GameId gameId, String sessionId, Instant connectedAt) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(connectedAt, "connectedAt must not be null");

        final var connection = new PlayerConnection(playerId, gameId, sessionId, connectedAt);
        connectionsBySession.put(sessionId, connection);
        connectionsByPlayer.put(playerId, connection);
        absences.remove(playerId);
    }

    /**
     * @param disconnectedAt when they went, which is what the sweep for abandoned seats
     *                       measures from
     */
    public Optional<PlayerConnection> playerDisconnected(String sessionId, Instant disconnectedAt) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(disconnectedAt, "disconnectedAt must not be null");

        final var connection = connectionsBySession.remove(sessionId);
        if (connection != null) {
            // A player who has already reconnected on a newer session is not away: only
            // drop them if the session that died is still the one they are known by.
            final var current = connectionsByPlayer.get(connection.playerId());
            if (current != null && current.sessionId().equals(sessionId)) {
                connectionsByPlayer.remove(connection.playerId());
                absences.put(
                    connection.playerId(),
                    new Absence(connection.playerId(), connection.gameId(), disconnectedAt)
                );
            }
        }
        return Optional.ofNullable(connection);
    }

    /**
     * Everyone who has been gone longer than {@code limit}.
     *
     * @param now the moment to measure against
     */
    public List<Absence> absencesLongerThan(Duration limit, Instant now) {
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return absences.values().stream()
            .filter(absence -> !absence.since().plus(limit).isAfter(now))
            .toList();
    }

    /**
     * Stops counting an absence, because it has been dealt with. Nothing is undone by
     * this: the seat, if it was handed to a bot, stays handed over until its player
     * reconnects.
     */
    public void forgetAbsence(PlayerId playerId) {
        absences.remove(Objects.requireNonNull(playerId, "playerId must not be null"));
    }

    public Set<PlayerId> getConnectedPlayers(GameId gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");

        return connectionsByPlayer.values().stream()
            .filter(c -> c.gameId().equals(gameId))
            .map(PlayerConnection::playerId)
            .collect(Collectors.toSet());
    }

    public Optional<PlayerConnection> getConnection(PlayerId playerId) {
        return Optional.ofNullable(connectionsByPlayer.get(playerId));
    }

    public boolean isConnected(PlayerId playerId) {
        return connectionsByPlayer.containsKey(playerId);
    }

    // Test helper
    public void clear() {
        connectionsBySession.clear();
        connectionsByPlayer.clear();
        absences.clear();
    }
}
