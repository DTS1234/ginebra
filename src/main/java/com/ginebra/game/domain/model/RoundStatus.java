package com.ginebra.game.domain.model;

/**
 * Status of a round in the game.
 */
public enum RoundStatus {

    /**
     * Waiting for players to pass or declare Soledad.
     */
    WAITING_FOR_SOLEDAD,

    /**
     * Waiting for the player who goes to select the trump suit.
     */
    WAITING_FOR_TRUMP,

    /**
     * Round is in progress - basas are being played.
     */
    IN_PROGRESS,

    /**
     * Round is complete - a team won or it's a draw.
     */
    COMPLETE
}
