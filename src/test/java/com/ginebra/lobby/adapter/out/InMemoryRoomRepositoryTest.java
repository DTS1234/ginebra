package com.ginebra.lobby.adapter.out;

import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;
import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRoomRepositoryTest {

    private InMemoryRoomRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoomRepository();
    }

    @Test
    void shouldSaveAndFindRoom() {
        final var roomId = RoomId.generate();
        final var room = Room.create(roomId, Instant.now());

        repository.save(room);

        final var found = repository.findById(roomId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(roomId);
    }

    @Test
    void shouldReturnEmptyWhenRoomNotFound() {
        final var nonExistentId = RoomId.generate();

        final var result = repository.findById(nonExistentId);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldUpdateExistingRoom() {
        final var roomId = RoomId.generate();
        final var room = Room.create(roomId, Instant.now());
        repository.save(room);

        // Add a player to the room
        room.addPlayer(PlayerId.generate(), "TestPlayer", Instant.now());
        repository.save(room); // Update

        final var found = repository.findById(roomId);
        assertThat(found).isPresent();
        assertThat(found.get().players()).hasSize(1);
    }

    @Test
    void shouldFindAllRooms() {
        final var room1 = Room.create(RoomId.generate(), Instant.now());
        final var room2 = Room.create(RoomId.generate(), Instant.now());
        repository.save(room1);
        repository.save(room2);

        final var allRooms = repository.findAll();

        assertThat(allRooms).hasSize(2);
        assertThat(allRooms).containsExactlyInAnyOrder(room1, room2);
    }

    @Test
    void shouldReturnDefensiveCopy() {
        final var room = Room.create(RoomId.generate(), Instant.now());
        repository.save(room);

        final var allRooms1 = repository.findAll();
        final var allRooms2 = repository.findAll();

        assertThat(allRooms1).isNotSameAs(allRooms2);
    }

    @Test
    void shouldClearAllRooms() {
        repository.save(Room.create(RoomId.generate(), Instant.now()));
        repository.save(Room.create(RoomId.generate(), Instant.now()));
        assertThat(repository.size()).isEqualTo(2);

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectNullRoom() {
        assertThatThrownBy(() -> repository.save(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("room must not be null");
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> repository.findById(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id must not be null");
    }
}
