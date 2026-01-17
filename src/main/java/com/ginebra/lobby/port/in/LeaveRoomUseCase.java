package com.ginebra.lobby.port.in;

import java.util.Objects;

public interface LeaveRoomUseCase {

    LeaveRoomResult leaveRoom(LeaveRoomCommand command);

    record LeaveRoomCommand(String roomId) {
        public LeaveRoomCommand {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("roomId must not be blank");
            }
        }
    }

    sealed interface LeaveRoomResult {

        record Success() implements LeaveRoomResult {}

        record RoomNotFound() implements LeaveRoomResult {}

        record PlayerNotInRoom() implements LeaveRoomResult {}

        record CannotLeaveAfterStarting(String currentStatus) implements LeaveRoomResult {
            public CannotLeaveAfterStarting {
                Objects.requireNonNull(currentStatus, "currentStatus must not be null");
            }
        }
    }
}
