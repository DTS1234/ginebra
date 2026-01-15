package com.ginebra.lobby.port.in;

import java.util.List;

public interface CreateRoomUseCase {

    CreateRoomResponse createRoom(CreateRoomCommand command);

    record CreateRoomCommand() {
        // Empty for now - future: room options (private/public, etc.)
    }

    record CreateRoomResponse(
        String roomId,
        List<PlayerDto> players,
        String status
    ) {
        public CreateRoomResponse {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("roomId must not be blank");
            }
            if (players == null) {
                throw new IllegalArgumentException("players must not be null");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status must not be blank");
            }

            // Defensive copy for immutability
            players = List.copyOf(players);
        }
    }

    record PlayerDto(
        String playerId,
        String displayName
    ) {
        public PlayerDto {
            if (playerId == null || playerId.isBlank()) {
                throw new IllegalArgumentException("playerId must not be blank");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }
}
