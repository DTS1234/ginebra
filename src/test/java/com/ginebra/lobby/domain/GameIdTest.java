package com.ginebra.lobby.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameIdTest {

    @Test
    void shouldCreateWithUUID() {
        final var uuid = UUID.randomUUID();

        final var gameId = new GameId(uuid);

        assertThat(gameId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldRejectNullUUID() {
        assertThatThrownBy(() -> new GameId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldGenerateUniqueIds() {
        final var id1 = GameId.generate();
        final var id2 = GameId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    void shouldConvertToString() {
        final var uuid = UUID.randomUUID();
        final var gameId = new GameId(uuid);

        final var result = gameId.toString();

        assertThat(result).isEqualTo(uuid.toString());
    }
}
