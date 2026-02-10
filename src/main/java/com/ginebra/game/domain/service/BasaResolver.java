package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Basa;
import com.ginebra.game.domain.model.BasaStatus;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;

import java.util.Objects;

/**
 * Determines the winner of a completed basa (trick).
 * Uses CardRankingService to find the winning card, then maps it back to the PlayerId.
 */
public class BasaResolver {

    private final CardRankingService cardRankingService;

    public BasaResolver(CardRankingService cardRankingService) {
        this.cardRankingService = Objects.requireNonNull(cardRankingService, "cardRankingService must not be null");
    }

    /**
     * Resolves the winner of a completed basa.
     *
     * @param basa the basa with all 5 cards played (status IN_PROGRESS, 5 cards)
     * @param trumpSuit the trump suit for this round
     * @return the PlayerId of the winner
     * @throws IllegalArgumentException if basa does not have exactly 5 cards
     */
    public PlayerId resolveWinner(Basa basa, Suit trumpSuit) {
        Objects.requireNonNull(basa, "basa must not be null");
        Objects.requireNonNull(trumpSuit, "trumpSuit must not be null");

        if (basa.cardCount() != Basa.CARDS_PER_BASA) {
            throw new IllegalArgumentException(
                "Basa must have " + Basa.CARDS_PER_BASA + " cards to resolve winner, has " + basa.cardCount()
            );
        }

        if (basa.status() == BasaStatus.COMPLETE) {
            throw new IllegalArgumentException("Basa is already complete with a winner");
        }

        final var firstCard = basa.cardsPlayed().get(0).card();
        final var effectiveLedSuit = MoveValidator.effectiveLedSuit(firstCard, trumpSuit);

        final var winningCard = cardRankingService.findWinner(trumpSuit, effectiveLedSuit, basa.cards());

        return basa.findPlayedCard(winningCard).playerId();
    }
}
