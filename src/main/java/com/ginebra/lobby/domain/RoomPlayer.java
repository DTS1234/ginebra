package com.ginebra.lobby.domain;

import com.ginebra.identity.domain.PlayerId;

import java.time.Instant;
import java.util.Objects;

public record RoomPlayer(
    PlayerId playerId,
    String displayName,
    Instant joinedAt
) {

    public RoomPlayer {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");

        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayName.length() > 100) {
            throw new IllegalArgumentException("displayName must not exceed 100 characters");
        }
    }

    public static RoomPlayer create(PlayerId playerId, String displayName, Instant joinedAt) {
        return new RoomPlayer(playerId, displayName, joinedAt);
    }
}
