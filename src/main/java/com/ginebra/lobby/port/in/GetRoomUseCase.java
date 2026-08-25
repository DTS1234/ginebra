package com.ginebra.lobby.port.in;

import java.util.List;
import java.util.Objects;

/**
 * Reads a single room a player belongs to.
 *
 * Joining is what normally hands a player the game id, but only the fifth player to join
 * fills the room, so the other four never see it. This lets a member look the room up and
 * find out whether it has become a game yet.
 */
public interface GetRoomUseCase {

    GetRoomResult getRoom(GetRoomCommand command);

    record GetRoomCommand(String roomId) {
        public GetRoomCommand {
            if (roomId == null || roomId.isBlank()) {
                throw new IllegalArgumentException("roomId must not be blank");
            }
        }
    }

    sealed interface GetRoomResult {

        record Success(
            String roomId,
            List<PlayerDto> players,
            String status,
            String gameId
        ) implements GetRoomResult {
            public Success {
                Objects.requireNonNull(roomId, "roomId must not be null");
                Objects.requireNonNull(players, "players must not be null");
                Objects.requireNonNull(status, "status must not be null");
                players = List.copyOf(players);
            }
        }

        record RoomNotFound() implements GetRoomResult {}

        /** The caller is not in this room, so its contents are none of their business. */
        record NotAMember() implements GetRoomResult {}
    }

    record PlayerDto(String playerId, String displayName) {}
}
