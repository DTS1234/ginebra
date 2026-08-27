package com.ginebra.lobby.port.in;

import java.util.List;

/**
 * Sits bots in every empty seat of a room, which fills it and starts the game.
 */
public interface FillWithBotsUseCase {

    FillWithBotsResult fillWithBots(FillWithBotsCommand command);

    record FillWithBotsCommand(String roomId) {}

    sealed interface FillWithBotsResult {

        record Success(
            String roomId,
            List<PlayerDto> players,
            String status,
            String gameId,
            String websocketUrl
        ) implements FillWithBotsResult {}

        record RoomNotFound() implements FillWithBotsResult {}

        record NotAMember() implements FillWithBotsResult {}

        /** The room was not waiting for players - it is already playing, or finished. */
        record RoomNotWaiting(String currentStatus) implements FillWithBotsResult {}
    }

    record PlayerDto(String playerId, String displayName) {}
}
