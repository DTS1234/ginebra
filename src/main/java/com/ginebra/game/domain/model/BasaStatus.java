package com.ginebra.game.domain.model;

/**
 * Status of a basa (trick) in the game.
 */
public enum BasaStatus {

    /**
     * Basa is in progress - cards are being played (0-4 cards played).
     */
    IN_PROGRESS,

    /**
     * Basa is complete - all 5 cards have been played and winner determined.
     */
    COMPLETE
}
