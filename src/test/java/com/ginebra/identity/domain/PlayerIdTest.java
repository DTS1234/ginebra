package com.ginebra.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerIdTest {

    @Test
    void shouldCreatePlayerIdWithUUID() {
        // Arrange
        final var uuid = UUID.randomUUID();

        // Act
        final var playerId = new PlayerId(uuid);

        // Assert
        assertThat(playerId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldRejectNullUUID() {
        // Act & Assert
        assertThatThrownBy(() -> new PlayerId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldGenerateUniquePlayerIds() {
        // Act
        final var playerId1 = PlayerId.generate();
        final var playerId2 = PlayerId.generate();

        // Assert
        assertThat(playerId1).isNotEqualTo(playerId2);
        assertThat(playerId1.value()).isNotEqualTo(playerId2.value());
    }

    @Test
    void shouldConvertToString() {
        // Arrange
        final var uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        final var playerId = new PlayerId(uuid);

        // Act
        final var result = playerId.toString();

        // Assert
        assertThat(result).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }
}
