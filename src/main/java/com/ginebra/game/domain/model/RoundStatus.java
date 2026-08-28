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
     * The going side has reached 5 basas with a clean sweep, so "fer todo" is still
     * reachable and theirs to call. Play is paused on that decision.
     */
    WAITING_FOR_TODO,

    /**
     * The king of the one who goes was forced out of them. Play is paused while they
     * decide whether to carry on alone or stop and pay 1.
     */
    WAITING_FOR_KING_CHOICE,

    /**
     * Round is complete.
     */
    COMPLETE
}
