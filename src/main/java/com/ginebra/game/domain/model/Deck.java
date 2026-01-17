package com.ginebra.game.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Represents a Spanish deck (Baraja Española) of 40 cards.
 * Immutable - shuffle and deal operations return new instances or copies.
 */
public class Deck {

    public static final int TOTAL_CARDS = 40;
    public static final int CARDS_PER_PLAYER = 8;
    public static final int PLAYER_COUNT = 5;

    private final List<Card> cards;

    private Deck(List<Card> cards) {
        Objects.requireNonNull(cards, "cards must not be null");
        this.cards = List.copyOf(cards);
    }

    /**
     * Creates a full 40-card Spanish deck with all suits and ranks.
     */
    public static Deck create() {
        final var cards = new ArrayList<Card>(TOTAL_CARDS);

        for (final var suit : Suit.values()) {
            for (final var rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }

        return new Deck(cards);
    }

    /**
     * Returns a new deck with cards in random order.
     *
     * @param random the random source for shuffling (allows deterministic testing)
     * @return a new shuffled deck
     */
    public Deck shuffle(Random random) {
        Objects.requireNonNull(random, "random must not be null");

        final var shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled, random);
        return new Deck(shuffled);
    }

    /**
     * Deals cards evenly to the specified number of players.
     * Each player receives (total cards / playerCount) cards.
     *
     * @param playerCount number of players to deal to
     * @return list of hands, one per player
     * @throws IllegalArgumentException if cards cannot be dealt evenly
     */
    public List<List<Card>> deal(int playerCount) {
        if (playerCount <= 0) {
            throw new IllegalArgumentException("playerCount must be positive");
        }
        if (cards.size() % playerCount != 0) {
            throw new IllegalArgumentException(
                "Cannot deal " + cards.size() + " cards evenly to " + playerCount + " players"
            );
        }

        final var cardsPerPlayer = cards.size() / playerCount;
        final var hands = new ArrayList<List<Card>>(playerCount);

        for (var i = 0; i < playerCount; i++) {
            final var start = i * cardsPerPlayer;
            final var end = start + cardsPerPlayer;
            hands.add(List.copyOf(cards.subList(start, end)));
        }

        return List.copyOf(hands);
    }

    /**
     * Returns an unmodifiable copy of the cards in this deck.
     */
    public List<Card> cards() {
        return cards; // Already immutable from constructor
    }

    /**
     * Returns the number of cards in this deck.
     */
    public int size() {
        return cards.size();
    }

    /**
     * Returns true if this deck has no cards.
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }

    /**
     * Finds and returns the Espadilla (Ace of Espadas) holder's index after dealing.
     * Used to determine who starts the first round.
     *
     * @param hands the dealt hands
     * @return the index of the player holding the Espadilla
     */
    public static int findEspadillaHolder(List<List<Card>> hands) {
        Objects.requireNonNull(hands, "hands must not be null");

        for (var i = 0; i < hands.size(); i++) {
            for (final var card : hands.get(i)) {
                if (card.isEspadilla()) {
                    return i;
                }
            }
        }

        throw new IllegalStateException("Espadilla not found in any hand");
    }
}
