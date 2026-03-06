package com.ginebra.lobby.port.in;

import java.util.List;
import java.util.Objects;

public interface JoinRoomUseCase {

    JoinRoomResult joinRoom(JoinRoomCommand command);

    record JoinRoomCommand(String roomId) {
        public JoinRoomCommand {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("roomId must not be blank");
            }
        }
    }

    sealed interface JoinRoomResult {

        record Success(
            String roomId,
            List<PlayerDto> players,
            String status,
            String gameId,
            String websocketUrl
        ) implements JoinRoomResult {
            public Success {
                Objects.requireNonNull(roomId, "roomId must not be null");
                Objects.requireNonNull(players, "players must not be null");
                Objects.requireNonNull(status, "status must not be null");
                players = List.copyOf(players);
                // gameId and websocketUrl are nullable (only set when game starts)
            }
        }

        record RoomNotFound() implements JoinRoomResult {}

        record RoomFull() implements JoinRoomResult {}

        record RoomNotWaiting(String currentStatus) implements JoinRoomResult {
            public RoomNotWaiting {
                Objects.requireNonNull(currentStatus, "currentStatus must not be null");
            }
        }

        record PlayerAlreadyInRoom() implements JoinRoomResult {}
    }

    record PlayerDto(String playerId, String displayName) {
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
