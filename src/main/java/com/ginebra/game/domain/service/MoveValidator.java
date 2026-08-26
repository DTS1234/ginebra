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
 * A third rule constrains the player who <i>opens</i> a basa, while no King has appeared:
 * <i>"Després has de tirar un altre pal fins que isca o posen rei"</i> (§4.4) - they must
 * lead a <b>different suit</b> from the one they led last time, which is how the King gets
 * smoked out. It lapses the moment a King decides the side, and yields if the leader holds
 * nothing else.
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
     * Validates a play in a basa that is already under way, or a lead with no
     * change-of-suit obligation to honour.
     *
     * @see #validate(List, Card, Suit, Optional, LeadContext)
     */
    public MoveValidation validate(
        List<Card> hand,
        Card cardToPlay,
        Suit trumpSuit,
        Optional<Card> firstCardInBasa
    ) {
        return validate(hand, cardToPlay, trumpSuit, firstCardInBasa, LeadContext.unconstrained());
    }

    /**
     * What the leader of a basa needs to know beyond their own hand: the suit they led last
     * time, and whether a King has already decided the side.
     *
     * @param previousLedSuit the effective suit led in the previous basa, empty on the first
     * @param sideDecided whether a King has appeared, which lifts the obligation
     */
    public record LeadContext(Optional<Suit> previousLedSuit, boolean sideDecided) {

        public LeadContext {
            Objects.requireNonNull(previousLedSuit, "previousLedSuit must not be null");
        }

        /** No obligation to honour - the first basa, or a round whose side is settled. */
        public static LeadContext unconstrained() {
            return new LeadContext(Optional.empty(), true);
        }
    }

    /**
     * Validates whether a card play is legal.
     *
     * @param hand the player's current hand
     * @param cardToPlay the card the player wants to play
     * @param trumpSuit the trump suit for this round
     * @param firstCardInBasa the first card played in the current basa, or empty if this is the first card
     * @param leadContext what the leader must change suit away from, if anything
     * @return Valid if the play is legal, Invalid with error code and message otherwise
     */
    public MoveValidation validate(
        List<Card> hand,
        Card cardToPlay,
        Suit trumpSuit,
        Optional<Card> firstCardInBasa,
        LeadContext leadContext
    ) {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(cardToPlay, "cardToPlay must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");
        Objects.requireNonNull(firstCardInBasa, "firstCardInBasa must not be null");
        Objects.requireNonNull(leadContext, "leadContext must not be null");

        if (!hand.contains(cardToPlay)) {
            return new MoveValidation.Invalid("INVALID_CARD", "Card not in player's hand: " + cardToPlay);
        }

        if (firstCardInBasa.isEmpty()) {
            return validateLead(hand, cardToPlay, trumpSuit, leadContext);
        }

        final var ledCard = firstCardInBasa.get();

        if (ledCard.isTrump(trumpSuit)) {
            return validateTrumpLead(hand, cardToPlay, trumpSuit, ledCard);
        }
        return validatePlainSuitLead(hand, cardToPlay, ledCard.suit());
    }

    /**
     * Opening a basa: free, except that while no King has appeared the leader must lead a
     * different suit from the one they led last time (rules-source.md §4.4).
     *
     * The obligation yields to what is possible - a leader holding nothing outside that
     * suit leads it - because the source states a duty, not a way to make a hand unplayable.
     */
    private MoveValidation validateLead(
        List<Card> hand,
        Card cardToPlay,
        Suit trumpSuit,
        LeadContext leadContext
    ) {
        if (leadContext.sideDecided() || leadContext.previousLedSuit().isEmpty()) {
            return new MoveValidation.Valid();
        }

        final var previous = leadContext.previousLedSuit().get();
        if (effectiveLedSuit(cardToPlay, trumpSuit) != previous) {
            return new MoveValidation.Valid();
        }

        final var canChange = hand.stream()
            .anyMatch(c -> effectiveLedSuit(c, trumpSuit) != previous);

        if (canChange) {
            return new MoveValidation.Invalid(
                "MUST_CHANGE_SUIT",
                "Must lead a suit other than " + previous + " until a King comes out"
            );
        }

        return new MoveValidation.Valid();
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
