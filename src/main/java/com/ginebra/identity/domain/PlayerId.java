package com.ginebra.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record PlayerId(UUID value) {
    public PlayerId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PlayerId generate() {
        return new PlayerId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
