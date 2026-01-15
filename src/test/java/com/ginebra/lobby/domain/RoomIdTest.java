package com.ginebra.lobby.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomIdTest {

    @Test
    void shouldCreateWithUUID() {
        final var uuid = UUID.randomUUID();

        final var roomId = new RoomId(uuid);

        assertThat(roomId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldRejectNullUUID() {
        assertThatThrownBy(() -> new RoomId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("value must not be null");
    }

    @Test
    void shouldGenerateUniqueIds() {
        final var id1 = RoomId.generate();
        final var id2 = RoomId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    void shouldConvertToString() {
        final var uuid = UUID.randomUUID();
        final var roomId = new RoomId(uuid);

        final var result = roomId.toString();

        assertThat(result).isEqualTo(uuid.toString());
    }
}
