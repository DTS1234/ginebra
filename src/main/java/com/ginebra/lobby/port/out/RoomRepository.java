package com.ginebra.lobby.port.out;

import com.ginebra.lobby.domain.Room;
import com.ginebra.lobby.domain.RoomId;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {
    void save(Room room);
    Optional<Room> findById(RoomId id);
    List<Room> findAll();
}
