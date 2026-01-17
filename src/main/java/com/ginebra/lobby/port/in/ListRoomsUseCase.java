package com.ginebra.lobby.port.in;

import java.util.List;
import java.util.Objects;

public interface ListRoomsUseCase {

    ListRoomsResponse listRooms();

    record ListRoomsResponse(List<RoomSummaryDto> rooms) {
        public ListRoomsResponse {
            Objects.requireNonNull(rooms, "rooms must not be null");
            rooms = List.copyOf(rooms);
        }
    }

    record RoomSummaryDto(
        String roomId,
        int playerCount,
        String createdAt
    ) {
        public RoomSummaryDto {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("roomId must not be blank");
            }
            if (playerCount < 0 || playerCount >= 5) {
                throw new IllegalArgumentException("playerCount must be 0-4 for joinable rooms");
            }
            if (createdAt == null || createdAt.isBlank()) {
                throw new IllegalArgumentException("createdAt must not be blank");
            }
        }
    }
}
