package com.ginebra.lobby.domain;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomPlayerTest {

    @Test
    void shouldCreateWithValidData() {
        final var playerId = PlayerId.generate();
        final var displayName = "Player1";
        final var joinedAt = Instant.now();

        final var roomPlayer = new RoomPlayer(playerId, displayName, joinedAt);

        assertThat(roomPlayer.playerId()).isEqualTo(playerId);
        assertThat(roomPlayer.displayName()).isEqualTo(displayName);
        assertThat(roomPlayer.joinedAt()).isEqualTo(joinedAt);
    }

    @Test
    void shouldCreateUsingFactoryMethod() {
        final var playerId = PlayerId.generate();
        final var displayName = "Player1";
        final var joinedAt = Instant.now();

        final var roomPlayer = RoomPlayer.create(playerId, displayName, joinedAt);

        assertThat(roomPlayer.playerId()).isEqualTo(playerId);
        assertThat(roomPlayer.displayName()).isEqualTo(displayName);
        assertThat(roomPlayer.joinedAt()).isEqualTo(joinedAt);
    }

    @Test
    void shouldRejectNullPlayerId() {
        assertThatThrownBy(() -> new RoomPlayer(null, "Player1", Instant.now()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("playerId must not be null");
    }

    @Test
    void shouldRejectNullDisplayName() {
        assertThatThrownBy(() -> new RoomPlayer(PlayerId.generate(), null, Instant.now()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("displayName must not be null");
    }

    @Test
    void shouldRejectNullJoinedAt() {
        assertThatThrownBy(() -> new RoomPlayer(PlayerId.generate(), "Player1", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("joinedAt must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void shouldRejectBlankDisplayName(String blankName) {
        assertThatThrownBy(() -> new RoomPlayer(PlayerId.generate(), blankName, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName must not be blank");
    }

    @Test
    void shouldRejectDisplayNameExceeding100Characters() {
        final var tooLongName = "a".repeat(101);

        assertThatThrownBy(() -> new RoomPlayer(PlayerId.generate(), tooLongName, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName must not exceed 100 characters");
    }

    @Test
    void shouldAcceptDisplayNameWith100Characters() {
        final var exactlyMaxLength = "a".repeat(100);

        final var roomPlayer = new RoomPlayer(PlayerId.generate(), exactlyMaxLength, Instant.now());

        assertThat(roomPlayer.displayName()).hasSize(100);
    }
}
