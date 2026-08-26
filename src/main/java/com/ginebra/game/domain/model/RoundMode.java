package com.ginebra.game.domain.model;

/**
 * How the side that "goes" came to be, which decides both the shape of the round and the
 * base rate it settles at (rules-source.md §5.1).
 *
 * A round has no mode until the going side is determined: either someone puts a king, the
 * one who goes puts their own, Soledad is declared, or the deal itself ends it.
 */
public enum RoundMode {

    /** Another player put the king on the one who goes. Two against three, base 2 each. */
    HELPED,

    /** "Posar-se el rei" - the one who goes played their own king. One against four, base 4. */
    SELF_KING,

    /** "Anar a soles" - declared before play. One against four, base 5. */
    SOLEDAD,

    /** One player was dealt all four kings: the hand ends before a card is played, base 4. */
    FOUR_KINGS,

    /** The king of the one who goes was forced out, which ends the hand with no side formed. */
    KING_FELL
}
