package com.ginebra.connection.domain;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.time.Instant;
import java.util.Objects;

public record PlayerConnection(
    PlayerId playerId,
    GameId gameId,
    String sessionId,
    Instant connectedAt
) {
    public PlayerConnection {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(connectedAt, "connectedAt must not be null");
    }
}
