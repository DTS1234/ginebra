package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Suit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates whether a card can be legally played according to Ginebra rules.
 *
 * Two obligations, depending on what was led (rules-source.md §4.4 and §4.5):
 *
 * - <b>A plain suit is led.</b> You must follow it if you hold it. Trumping - "fallar" - is
 *   available only when you are void: <i>"Si no en tens, pots «fallar», si vols."</i> The
 *   Espadilla and the Basto are trumps, so they are not an escape from following a plain
 *   suit; nor do they count as members of Espadas and Bastos when one of those is led.
 *
 * - <b>A trump is led.</b> You must play a trump if you hold one, except that you may
 *   withhold a special card - Espadilla, Manilla or Basto - that outranks the card led.
 *   That single rule reproduces all four cases the source spells out: nothing is exempt
 *   from an Espadilla lead, the Espadilla is exempt from a Manilla lead, the Espadilla and
 *   Manilla from a Basto lead, and all three from any other trump.
 *
 * Players are never required to "kill" - playing under the current best card is legal.
 */
public class MoveValidator {

    /** Effective ranks 1, 2 and 3 are the Espadilla, the Manilla and the Basto. */
    private static final int LOWEST_SPECIAL_RANK = 3;

    private final CardRankingService cardRankingService;

    public MoveValidator(CardRankingService cardRankingService) {
        this.cardRankingService = Objects.requireNonNull(
            cardRankingService, "cardRankingService must not be null"
        );
    }

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

        final var ledCard = firstCardInBasa.get();

        if (ledCard.isTrump(trumpSuit)) {
            return validateTrumpLead(hand, cardToPlay, trumpSuit, ledCard);
        }
        return validatePlainSuitLead(hand, cardToPlay, ledCard.suit());
    }

    /**
     * A trump was led: a trump must be played unless every trump held may be withheld.
     */
    private MoveValidation validateTrumpLead(
        List<Card> hand,
        Card cardToPlay,
        Suit trumpSuit,
        Card ledCard
    ) {
        if (cardToPlay.isTrump(trumpSuit)) {
            return new MoveValidation.Valid();
        }

        final var holdsCompellingTrump = hand.stream()
            .filter(c -> c.isTrump(trumpSuit))
            .anyMatch(c -> !mayWithhold(c, ledCard, trumpSuit));

        if (holdsCompellingTrump) {
            return new MoveValidation.Invalid(
                "MUST_PLAY_TRUMP",
                "Must play a trump: " + ledCard + " was led and you hold a trump you cannot withhold"
            );
        }

        return new MoveValidation.Valid();
    }

    /**
     * A plain suit was led: it must be followed by a player who holds it.
     */
    private MoveValidation validatePlainSuitLead(List<Card> hand, Card cardToPlay, Suit ledSuit) {
        final var holdsLedSuit = hand.stream().anyMatch(c -> c.followsSuit(ledSuit));

        if (holdsLedSuit && !cardToPlay.followsSuit(ledSuit)) {
            return new MoveValidation.Invalid(
                "MUST_FOLLOW_SUIT",
                "Must play " + ledSuit + " (you have cards of that suit)"
            );
        }

        return new MoveValidation.Valid();
    }

    /**
     * Whether a trump may be kept back when {@code ledCard} was led.
     *
     * Only the three special cards may, and only over a card they outrank. An ordinary
     * trump that happens to beat the card led carries no such privilege.
     */
    private boolean mayWithhold(Card card, Card ledCard, Suit trumpSuit) {
        final var rank = trumpRank(card, trumpSuit);
        return rank <= LOWEST_SPECIAL_RANK && rank < trumpRank(ledCard, trumpSuit);
    }

    /** Rank within the trump order, where 1, 2 and 3 are Espadilla, Manilla and Basto. */
    private int trumpRank(Card card, Suit trumpSuit) {
        return cardRankingService.getEffectiveRank(trumpSuit, trumpSuit, card);
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
