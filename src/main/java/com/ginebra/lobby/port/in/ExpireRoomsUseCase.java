package com.ginebra.lobby.port.in;

import com.ginebra.lobby.domain.RoomId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Clears out rooms nobody came back to.
 *
 * A room that never filled sits in the lobby forever, and the next five people to arrive
 * have to guess which of the listed rooms are real. Half an hour of nothing (design §8)
 * is long enough to say.
 */
public interface ExpireRoomsUseCase {

    /**
     * @param idleFor how long a room must have sat untouched to count as abandoned
     * @param sweptAt the moment to measure that against
     * @return the rooms that were deleted
     */
    List<RoomId> expireStaleRooms(Duration idleFor, Instant sweptAt);
}
