package com.ginebra.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerIdentityTest {

    @Test
    void shouldCreatePlayerIdentity() {
        // Arrange
        final var playerId = PlayerId.generate();

        // Act
        final var identity = new PlayerIdentity(playerId, "Player123", false);

        // Assert
        assertThat(identity.playerId()).isEqualTo(playerId);
        assertThat(identity.displayName()).isEqualTo("Player123");
        assertThat(identity.anonymous()).isFalse();
    }

    @Test
    void shouldRejectNullPlayerId() {
        // Act & Assert
        assertThatThrownBy(() -> new PlayerIdentity(null, "Player", false))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("playerId must not be null");
    }

    @Test
    void shouldRejectNullDisplayName() {
        // Arrange
        final var playerId = PlayerId.generate();

        // Act & Assert
        assertThatThrownBy(() -> new PlayerIdentity(playerId, null, false))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("displayName must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void shouldRejectBlankDisplayName(String blankName) {
        // Arrange
        final var playerId = PlayerId.generate();

        // Act & Assert
        assertThatThrownBy(() -> new PlayerIdentity(playerId, blankName, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName must not be blank");
    }

    @Test
    void shouldRejectDisplayNameExceeding100Characters() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var longName = "a".repeat(101);

        // Act & Assert
        assertThatThrownBy(() -> new PlayerIdentity(playerId, longName, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName must not exceed 100 characters");
    }

    @Test
    void shouldCreateAnonymousWithProvidedDisplayName() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var requestedName = Optional.of("CustomName");

        // Act
        final var identity = PlayerIdentity.createAnonymous(playerId, requestedName);

        // Assert
        assertThat(identity.playerId()).isEqualTo(playerId);
        assertThat(identity.displayName()).isEqualTo("CustomName");
        assertThat(identity.anonymous()).isTrue();
    }

    @Test
    void shouldCreateAnonymousWithGeneratedDisplayName() {
        // Arrange
        final var playerId = PlayerId.generate();

        // Act
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.empty());

        // Assert
        assertThat(identity.displayName()).startsWith("Guest_");
        assertThat(identity.displayName()).hasSize(10); // "Guest_" + 4 chars
        assertThat(identity.anonymous()).isTrue();
    }

    @Test
    void shouldGenerateUniqueDisplayNames() {
        // Act
        final var identity1 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.empty());
        final var identity2 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.empty());

        // Assert
        assertThat(identity1.displayName()).isNotEqualTo(identity2.displayName());
    }

    @Test
    void shouldUseGeneratedNameWhenProvidedNameIsBlank() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var blankName = Optional.of("  ");

        // Act
        final var identity = PlayerIdentity.createAnonymous(playerId, blankName);

        // Assert
        assertThat(identity.displayName()).startsWith("Guest_");
    }
}
