package com.ginebra.lobby.domain;

public sealed interface AddPlayerResult {

    record Success() implements AddPlayerResult {}

    record RoomFull() implements AddPlayerResult {}

    record RoomNotWaiting(RoomStatus currentStatus) implements AddPlayerResult {}

    record PlayerAlreadyInRoom() implements AddPlayerResult {}

    record RoomFull_GameShouldStart() implements AddPlayerResult {}
}
