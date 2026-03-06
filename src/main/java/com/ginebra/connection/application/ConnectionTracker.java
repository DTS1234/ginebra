package com.ginebra.connection.application;

import com.ginebra.connection.domain.PlayerConnection;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks active WebSocket connections per player and per game.
 * Thread-safe for concurrent connection/disconnection events.
 */
@Component
public class ConnectionTracker {

    private final ConcurrentHashMap<String, PlayerConnection> connectionsBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PlayerId, PlayerConnection> connectionsByPlayer = new ConcurrentHashMap<>();

    public void playerConnected(PlayerId playerId, GameId gameId, String sessionId, Instant connectedAt) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(connectedAt, "connectedAt must not be null");

        final var connection = new PlayerConnection(playerId, gameId, sessionId, connectedAt);
        connectionsBySession.put(sessionId, connection);
        connectionsByPlayer.put(playerId, connection);
    }

    public Optional<PlayerConnection> playerDisconnected(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        final var connection = connectionsBySession.remove(sessionId);
        if (connection != null) {
            connectionsByPlayer.remove(connection.playerId());
        }
        return Optional.ofNullable(connection);
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
    }
}
