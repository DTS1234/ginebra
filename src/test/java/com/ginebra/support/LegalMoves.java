package com.ginebra.support;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidation;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.identity.domain.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks a card a player is actually allowed to play.
 *
 * Tests that drive a whole round care about the flow, not about which card is chosen,
 * so they delegate the follow-suit rules to the production {@link MoveValidator}.
 */
public final class LegalMoves {

    private static final MoveValidator VALIDATOR = new MoveValidator(new CardRankingService());

    private LegalMoves() {
    }

    /**
     * Returns a card the given player may legally play in the round's current basa.
     */
    public static Card forRound(Round round, PlayerId player) {
        Objects.requireNonNull(round, "round must not be null");
        Objects.requireNonNull(player, "player must not be null");

        final var basa = round.currentBasa().orElseThrow(
            () -> new IllegalStateException("Round has no current basa")
        );
        final var firstCard = basa.cardsPlayed().isEmpty()
            ? Optional.<Card>empty()
            : Optional.of(basa.cardsPlayed().get(0).card());

        return fromHand(round.getHand(player), round.trumpSuit().orElseThrow(), firstCard);
    }

    /**
     * Returns a card from the hand that satisfies the follow-suit rules for the given basa.
     */
    public static Card fromHand(List<Card> hand, Suit trumpSuit, Optional<Card> firstCardInBasa) {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");
        Objects.requireNonNull(firstCardInBasa, "firstCardInBasa must not be null");

        for (final var card : hand) {
            if (VALIDATOR.validate(hand, card, trumpSuit, firstCardInBasa) instanceof MoveValidation.Valid) {
                return card;
            }
        }
        throw new IllegalStateException("No legal card in hand: " + hand);
    }

    /**
     * Returns a card from the hand that the player is NOT allowed to play, if one exists.
     */
    public static Optional<Card> illegalFromHand(List<Card> hand, Suit trumpSuit, Card firstCardInBasa) {
        Objects.requireNonNull(hand, "hand must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");
        Objects.requireNonNull(firstCardInBasa, "firstCardInBasa must not be null");

        final var first = Optional.of(firstCardInBasa);
        return hand.stream()
            .filter(card -> VALIDATOR.validate(hand, card, trumpSuit, first) instanceof MoveValidation.Invalid)
            .findFirst();
    }
}
