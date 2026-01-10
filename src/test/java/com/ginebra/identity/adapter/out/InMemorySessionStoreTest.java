package com.ginebra.identity.adapter.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySessionStoreTest {

    private InMemorySessionStore sessionStore;

    @BeforeEach
    void setUp() {
        sessionStore = new InMemorySessionStore();
    }

    @Test
    void shouldSaveAndRetrieveIdentity() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));

        // Act
        sessionStore.save(identity);
        final var result = sessionStore.findById(playerId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(identity);
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        // Arrange
        final var playerId = PlayerId.generate();

        // Act
        final var result = sessionStore.findById(playerId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteIdentity() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));
        sessionStore.save(identity);

        // Act
        sessionStore.deleteById(playerId);
        final var result = sessionStore.findById(playerId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldOverwriteExistingIdentity() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity1 = PlayerIdentity.createAnonymous(playerId, Optional.of("Player1"));
        final var identity2 = PlayerIdentity.createAnonymous(playerId, Optional.of("Player2"));

        // Act
        sessionStore.save(identity1);
        sessionStore.save(identity2);
        final var result = sessionStore.findById(playerId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().displayName()).isEqualTo("Player2");
    }

    @Test
    void shouldClearAllSessions() {
        // Arrange
        final var identity1 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player1"));
        final var identity2 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player2"));
        sessionStore.save(identity1);
        sessionStore.save(identity2);

        // Act
        sessionStore.clear();

        // Assert
        assertThat(sessionStore.size()).isZero();
        assertThat(sessionStore.findById(identity1.playerId())).isEmpty();
        assertThat(sessionStore.findById(identity2.playerId())).isEmpty();
    }

    @Test
    void shouldReportCorrectSize() {
        // Arrange
        final var identity1 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player1"));
        final var identity2 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player2"));

        // Act & Assert
        assertThat(sessionStore.size()).isZero();

        sessionStore.save(identity1);
        assertThat(sessionStore.size()).isEqualTo(1);

        sessionStore.save(identity2);
        assertThat(sessionStore.size()).isEqualTo(2);

        sessionStore.deleteById(identity1.playerId());
        assertThat(sessionStore.size()).isEqualTo(1);
    }

    @Test
    void shouldRejectNullIdentityOnSave() {
        // Act & Assert
        assertThatThrownBy(() -> sessionStore.save(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("identity must not be null");
    }

    @Test
    void shouldRejectNullIdOnFind() {
        // Act & Assert
        assertThatThrownBy(() -> sessionStore.findById(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectNullIdOnDelete() {
        // Act & Assert
        assertThatThrownBy(() -> sessionStore.deleteById(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id must not be null");
    }
}
