package com.ginebra.game.adapter.out;

import com.ginebra.game.domain.model.Game;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryGameRepositoryTest {

    private InMemoryGameRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameRepository();
    }

    @Test
    void shouldSaveAndFindGame() {
        // Arrange
        final var game = createTestGame();

        // Act
        repository.save(game);
        final var found = repository.findById(game.gameId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().gameId()).isEqualTo(game.gameId());
    }

    @Test
    void shouldReturnEmptyForNonexistentGame() {
        final var result = repository.findById(GameId.generate());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteGame() {
        // Arrange
        final var game = createTestGame();
        repository.save(game);

        // Act
        repository.delete(game.gameId());

        // Assert
        assertThat(repository.findById(game.gameId())).isEmpty();
        assertThat(repository.size()).isZero();
    }

    @Test
    void shouldOverwriteExistingGame() {
        // Arrange
        final var game = createTestGame();
        repository.save(game);

        final var updatedGame = game.selectTrump(com.ginebra.game.domain.model.Suit.COPAS);
        repository.save(updatedGame);

        // Assert
        assertThat(repository.size()).isEqualTo(1);
        final var found = repository.findById(game.gameId()).orElseThrow();
        assertThat(found.currentRound().orElseThrow().isInProgress()).isTrue();
    }

    @Test
    void shouldClear() {
        // Arrange
        repository.save(createTestGame());
        repository.save(createTestGame());

        // Act
        repository.clear();

        // Assert
        assertThat(repository.size()).isZero();
    }

    private Game createTestGame() {
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        return Game.start(GameId.generate(), players, new Random(42));
    }
}
