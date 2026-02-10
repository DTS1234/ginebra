package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Suit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates whether a card can be legally played according to Ginebra rules.
 *
 * Key rules:
 * - Espadilla and Basto are "free" cards: they can always be played regardless of led suit.
 * - Players must follow the effective led suit if they have non-special cards of that suit.
 * - When a special card leads, the effective led suit becomes the trump suit.
 * - Players are NOT required to "kill" (play a higher card).
 */
public class MoveValidator {

    /**
     * Validates whether a card play is legal.
     *
     * @param hand the player's current hand
     * @param cardToPlay the card the player wants to play
     * @param trumpSuit the trump suit for this round
     * @param firstCardInBasa the first card played in the current basa, or empty if this is the first card
     * @return Valid if the play is legal, Invalid with error code and message otherwise
     */
    public MoveValidation validate(
        List<Card> hand,
        Card cardToPlay,
        Suit trumpSuit,
        Optional<Card> firstCardInBasa
    ) {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(cardToPlay, "cardToPlay must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");
        Objects.requireNonNull(firstCardInBasa, "firstCardInBasa must not be null");

        if (!hand.contains(cardToPlay)) {
            return new MoveValidation.Invalid("INVALID_CARD", "Card not in player's hand: " + cardToPlay);
        }

        // First card of basa: any card is valid
        if (firstCardInBasa.isEmpty()) {
            return new MoveValidation.Valid();
        }

        // Special cards (Espadilla, Basto) can always be played
        if (cardToPlay.isSpecial()) {
            return new MoveValidation.Valid();
        }

        final var effectiveSuit = effectiveLedSuit(firstCardInBasa.get(), trumpSuit);

        final var hasNonSpecialOfLedSuit = hand.stream()
            .filter(c -> !c.isSpecial())
            .anyMatch(c -> c.suit() == effectiveSuit);

        if (hasNonSpecialOfLedSuit && cardToPlay.suit() != effectiveSuit) {
            return new MoveValidation.Invalid(
                "MUST_FOLLOW_SUIT",
                "Must play " + effectiveSuit + " (you have cards of that suit)"
            );
        }

        return new MoveValidation.Valid();
    }

    /**
     * Computes the effective led suit for a basa.
     * When a special card (Espadilla or Basto) leads, the effective led suit is the trump suit.
     * Otherwise, it is the first card's actual suit.
     *
     * @param firstCard the first card played in the basa
     * @param trumpSuit the trump suit for this round
     * @return the effective led suit
     */
    public static Suit effectiveLedSuit(Card firstCard, Suit trumpSuit) {
        Objects.requireNonNull(firstCard, "firstCard must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");

        return firstCard.isSpecial() ? trumpSuit : firstCard.suit();
    }
}
