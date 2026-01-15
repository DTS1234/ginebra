package com.ginebra.lobby.domain;

public sealed interface RemovePlayerResult {

    record Success() implements RemovePlayerResult {}

    record PlayerNotInRoom() implements RemovePlayerResult {}

    record CannotLeaveAfterStarting() implements RemovePlayerResult {}

    record RoomNowEmpty_ShouldDelete() implements RemovePlayerResult {}
}
