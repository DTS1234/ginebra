package com.ginebra.lobby.adapter.out;

import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;
import com.ginebra.lobby.port.out.RoomRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRoomRepository implements RoomRepository {

    private final ConcurrentHashMap<RoomId, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public void save(Room room) {
        Objects.requireNonNull(room, "room must not be null");
        rooms.put(room.id(), room);
    }

    @Override
    public Optional<Room> findById(RoomId id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(rooms.get(id));
    }

    @Override
    public List<Room> findAll() {
        return List.copyOf(rooms.values());
    }

    // Test helper methods
    public void clear() {
        rooms.clear();
    }

    public int size() {
        return rooms.size();
    }
}
