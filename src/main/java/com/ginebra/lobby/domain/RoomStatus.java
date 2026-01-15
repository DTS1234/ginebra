package com.ginebra.lobby.domain;

public enum RoomStatus {
    /**
     * Room is open for players to join.
     */
    WAITING,

    /**
     * 5th player joined, game creation triggered.
     */
    STARTING,

    /**
     * Successfully converted to game.
     */
    CONVERTED,

    /**
     * All players left before game started.
     */
    ABANDONED
}
