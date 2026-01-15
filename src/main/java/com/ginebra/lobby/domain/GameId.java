package com.ginebra.lobby.domain;

import java.util.Objects;
import java.util.UUID;

public record GameId(UUID value) {

    public GameId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static GameId generate() {
        return new GameId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
