package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Determines card rankings and compares cards based on trump suit and led suit.
 *
 * Key rules:
 * - Espadilla (Ace of Espadas) is ALWAYS the highest card
 * - Manilla is ALWAYS second (7 for Copas/Oros, 2 for Espadas/Bastos)
 * - Basto (Ace of Bastos) is ALWAYS third
 * - Trump cards beat all non-trump cards
 * - Led suit cards beat non-led, non-trump cards
 * - Low cards run 2 down to 7 in Copas and Oros, 7 down to 2 in Espadas and Bastos
 */
public class CardRankingService {

    // Trump suit rankings for COPAS and OROS (where 7 is manilla)
    // Lower number = higher rank (1 is best)
    private static final Map<Rank, Integer> COPAS_OROS_TRUMP_RANKS = Map.ofEntries(
        Map.entry(Rank.AS, 4),      // Ace (Carabassa/Rovell)
        Map.entry(Rank.REY, 5),     // King
        Map.entry(Rank.CABALLO, 6), // Horse
        Map.entry(Rank.SOTA, 7),    // Jack
        Map.entry(Rank.DOS, 8),
        Map.entry(Rank.TRES, 9),
        Map.entry(Rank.CUATRO, 10),
        Map.entry(Rank.CINCO, 11),
        Map.entry(Rank.SEIS, 12),
        Map.entry(Rank.SIETE, 2)    // Manilla - rank 2
    );

    // Trump suit rankings for ESPADAS and BASTOS (where 2 is manilla)
    private static final Map<Rank, Integer> ESPADAS_BASTOS_TRUMP_RANKS = Map.ofEntries(
        Map.entry(Rank.REY, 4),     // King
        Map.entry(Rank.CABALLO, 5), // Horse
        Map.entry(Rank.SOTA, 6),    // Jack
        Map.entry(Rank.SIETE, 7),
        Map.entry(Rank.SEIS, 8),
        Map.entry(Rank.CINCO, 9),
        Map.entry(Rank.CUATRO, 10),
        Map.entry(Rank.TRES, 11),
        Map.entry(Rank.DOS, 2)      // Manilla - rank 2
        // Note: AS is special (Espadilla/Basto), not in regular ranking
    );

    // Non-trump rankings for COPAS and OROS: their Ace is in play (Rovell), and the
    // low cards run 2 down to 7 - the same direction they take when the suit is trump.
    private static final Map<Rank, Integer> COPAS_OROS_NON_TRUMP_RANKS = Map.ofEntries(
        Map.entry(Rank.REY, 1),     // King - highest in non-trump
        Map.entry(Rank.CABALLO, 2), // Horse
        Map.entry(Rank.SOTA, 3),    // Jack
        Map.entry(Rank.AS, 4),      // Ace (Rovell)
        Map.entry(Rank.DOS, 5),
        Map.entry(Rank.TRES, 6),
        Map.entry(Rank.CUATRO, 7),
        Map.entry(Rank.CINCO, 8),
        Map.entry(Rank.SEIS, 9),
        Map.entry(Rank.SIETE, 10)
    );

    // Non-trump rankings for ESPADAS and BASTOS: their Ace is a special card so it never
    // appears here, and the low cards run 7 down to 2.
    private static final Map<Rank, Integer> ESPADAS_BASTOS_NON_TRUMP_RANKS = Map.ofEntries(
        Map.entry(Rank.REY, 1),
        Map.entry(Rank.CABALLO, 2),
        Map.entry(Rank.SOTA, 3),
        Map.entry(Rank.SIETE, 4),
        Map.entry(Rank.SEIS, 5),
        Map.entry(Rank.CINCO, 6),
        Map.entry(Rank.CUATRO, 7),
        Map.entry(Rank.TRES, 8),
        Map.entry(Rank.DOS, 9)
    );

    /**
     * Returns the Manilla card for the given trump suit.
     * - COPAS/OROS: 7 of that suit
     * - ESPADAS/BASTOS: 2 of that suit
     */
    public Card getManilla(Suit trump) {
        Objects.requireNonNull(trump, "trump must not be null");

        return switch (trump) {
            case COPAS, OROS -> new Card(trump, Rank.SIETE);
            case ESPADAS, BASTOS -> new Card(trump, Rank.DOS);
        };
    }

    /**
     * Compares two cards and returns the higher one.
     *
     * @param trump the trump suit for this round
     * @param ledSuit the suit that was led (first card played in basa)
     * @param card1 first card to compare
     * @param card2 second card to compare
     * @return the winning card
     */
    public Card higher(Suit trump, Suit ledSuit, Card card1, Card card2) {
        Objects.requireNonNull(trump, "trump must not be null");
        Objects.requireNonNull(ledSuit, "ledSuit must not be null");
        Objects.requireNonNull(card1, "card1 must not be null");
        Objects.requireNonNull(card2, "card2 must not be null");

        final var rank1 = getEffectiveRank(trump, ledSuit, card1);
        final var rank2 = getEffectiveRank(trump, ledSuit, card2);

        // Lower rank number = better card
        return rank1 <= rank2 ? card1 : card2;
    }

    /**
     * Gets the effective rank of a card in the current context.
     * Lower numbers are better (1 = Espadilla, always highest).
     *
     * Ranking tiers:
     * 1-3: Special cards (Espadilla, Manilla, Basto)
     * 100-113: Trump suit cards
     * 200-210: Led suit cards (non-trump)
     * 300-310: Other suits (can't win unless led)
     */
    public int getEffectiveRank(Suit trump, Suit ledSuit, Card card) {
        Objects.requireNonNull(trump, "trump must not be null");
        Objects.requireNonNull(ledSuit, "ledSuit must not be null");
        Objects.requireNonNull(card, "card must not be null");

        // Special cards always have fixed ranks
        if (card.isEspadilla()) {
            return 1;
        }
        if (card.isManilla(trump)) {
            return 2;
        }
        if (card.isBasto()) {
            return 3;
        }

        // Trump suit cards
        if (card.suit() == trump) {
            return 100 + getTrumpRank(trump, card);
        }

        // Led suit cards (non-trump)
        if (card.suit() == ledSuit) {
            return 200 + getNonTrumpRank(card);
        }

        // Other suits (can't beat led suit)
        return 300 + getNonTrumpRank(card);
    }

    /**
     * Finds the winning card from a list of played cards.
     *
     * @param trump the trump suit
     * @param ledSuit the suit that was led
     * @param playedCards the cards played in order
     * @return the winning card
     */
    public Card findWinner(Suit trump, Suit ledSuit, List<Card> playedCards) {
        Objects.requireNonNull(trump, "trump must not be null");
        Objects.requireNonNull(ledSuit, "ledSuit must not be null");
        Objects.requireNonNull(playedCards, "playedCards must not be null");

        if (playedCards.isEmpty()) {
            throw new IllegalArgumentException("playedCards must not be empty");
        }

        var winner = playedCards.get(0);
        for (var i = 1; i < playedCards.size(); i++) {
            winner = higher(trump, ledSuit, winner, playedCards.get(i));
        }
        return winner;
    }

    /**
     * Gets the rank of a card within the trump suit.
     * Does not handle special cards (Espadilla, Manilla, Basto).
     */
    private int getTrumpRank(Suit trump, Card card) {
        final var rankMap = switch (trump) {
            case COPAS, OROS -> COPAS_OROS_TRUMP_RANKS;
            case ESPADAS, BASTOS -> ESPADAS_BASTOS_TRUMP_RANKS;
        };
        return rankMap.getOrDefault(card.rank(), 99);
    }

    /**
     * Gets the rank of a card within a non-trump suit.
     *
     * The direction of the low cards depends on the suit, not on trump: Copas and Oros
     * run 2 down to 7, Espadas and Bastos run 7 down to 2 (spec 2.4).
     */
    private int getNonTrumpRank(Card card) {
        final var rankMap = switch (card.suit()) {
            case COPAS, OROS -> COPAS_OROS_NON_TRUMP_RANKS;
            case ESPADAS, BASTOS -> ESPADAS_BASTOS_NON_TRUMP_RANKS;
        };
        return rankMap.getOrDefault(card.rank(), 99);
    }
}
