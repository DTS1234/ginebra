package com.ginebra.lobby.domain;

import java.util.Objects;
import java.util.UUID;

public record RoomId(UUID value) {

    public RoomId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RoomId generate() {
        return new RoomId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
