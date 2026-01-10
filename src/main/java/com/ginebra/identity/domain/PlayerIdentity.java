package com.ginebra.identity.domain;

import java.util.Objects;
import java.util.Optional;

public record PlayerIdentity(
    PlayerId playerId,
    String displayName,
    boolean anonymous
) {
    public PlayerIdentity {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");

        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayName.length() > 100) {
            throw new IllegalArgumentException("displayName must not exceed 100 characters");
        }
    }

    public static PlayerIdentity createAnonymous(
        PlayerId playerId,
        Optional<String> requestedDisplayName
    ) {
        final var displayName = requestedDisplayName
            .filter(name -> !name.isBlank())
            .orElseGet(() -> generateAnonymousDisplayName(playerId));

        return new PlayerIdentity(playerId, displayName, true);
    }

    private static String generateAnonymousDisplayName(PlayerId playerId) {
        final var suffix = playerId.value().toString().substring(0, 4);
        return "Guest_" + suffix;
    }
}
