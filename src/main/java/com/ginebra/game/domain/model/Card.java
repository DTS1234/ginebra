package com.ginebra.game.domain.model;

import java.util.Objects;

/**
 * Represents a single card from the Spanish deck (Baraja Española).
 * Immutable value object.
 */
public record Card(Suit suit, Rank rank) {

    public Card {
        Objects.requireNonNull(suit, "suit must not be null");
        Objects.requireNonNull(rank, "rank must not be null");
    }

    // === Special Card Identifiers ===

    /**
     * Checks if this card is the Espadilla (Ace of Espadas).
     * The Espadilla is a universal high card that doesn't belong to Espadas
     * for suit-following purposes and can be played at any time.
     */
    public boolean isEspadilla() {
        return suit == Suit.ESPADAS && rank == Rank.AS;
    }

    /**
     * Checks if this card is the Basto (Ace of Bastos).
     * The Basto is a universal high card that doesn't belong to Bastos
     * for suit-following purposes and can be played at any time.
     */
    public boolean isBasto() {
        return suit == Suit.BASTOS && rank == Rank.AS;
    }

    /**
     * Checks if this card is a special card (Espadilla or Basto).
     * Special cards can be played at any time regardless of led suit.
     */
    public boolean isSpecial() {
        return isEspadilla() || isBasto();
    }

    // === Manilla Identifiers ===

    /**
     * Checks if this card is a potential Manilla.
     * The Manilla is the second-highest card and varies by trump suit:
     * - When Copas or Oros is trump: 7 of that suit is Manilla
     * - When Espadas or Bastos is trump: 2 of that suit is Manilla
     *
     * This method checks if the card COULD be a Manilla when its suit is trump.
     */
    public boolean isPotentialManilla() {
        return switch (suit) {
            case COPAS, OROS -> rank == Rank.SIETE;
            case ESPADAS, BASTOS -> rank == Rank.DOS;
        };
    }

    /**
     * Checks if this card is the Manilla for the given trump suit.
     *
     * @param trumpSuit the current trump suit
     * @return true if this card is the Manilla for the given trump
     */
    public boolean isManilla(Suit trumpSuit) {
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");

        if (suit != trumpSuit) {
            return false;
        }

        return switch (trumpSuit) {
            case COPAS, OROS -> rank == Rank.SIETE;
            case ESPADAS, BASTOS -> rank == Rank.DOS;
        };
    }

    // === Query Methods ===

    /**
     * Checks if this card is a King. Kings are significant for team formation.
     * Playing the first King reveals the partnership.
     */
    public boolean isKing() {
        return rank == Rank.REY;
    }

    // === Factory Methods ===

    /**
     * Creates the Espadilla (Ace of Espadas).
     */
    public static Card espadilla() {
        return new Card(Suit.ESPADAS, Rank.AS);
    }

    /**
     * Creates the Basto (Ace of Bastos).
     */
    public static Card basto() {
        return new Card(Suit.BASTOS, Rank.AS);
    }
}
