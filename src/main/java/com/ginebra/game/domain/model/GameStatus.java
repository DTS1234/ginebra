package com.ginebra.game.domain.model;

/**
 * Status of the overall game.
 */
public enum GameStatus {

    /**
     * A round is currently active.
     */
    IN_PROGRESS,

    /**
     * Game is over (a player went bankrupt).
     */
    ENDED
}
