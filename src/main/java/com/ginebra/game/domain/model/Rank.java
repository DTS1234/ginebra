package com.ginebra.game.domain.model;

/**
 * Represents the ten ranks of each suit in the Spanish deck.
 *
 * Note: Card ranking depends on the trump suit selected and is handled
 * by CardRankingService, not by the ordinal values of this enum.
 */
public enum Rank {
    AS,       // Ace
    DOS,      // 2
    TRES,     // 3
    CUATRO,   // 4
    CINCO,    // 5
    SEIS,     // 6
    SIETE,    // 7
    SOTA,     // Jack
    CABALLO,  // Horse/Knight
    REY       // King
}
