package com.ginebra.support;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Deck;
import com.ginebra.identity.domain.PlayerId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a deterministic 40-card deal for tests.
 *
 * Specific cards can be pinned to specific players; every remaining seat is filled
 * from the rest of the deck in a fixed order, so a deal is fully reproducible.
 *
 * <pre>
 * final var hands = TestDeal.forPlayers(players)
 *     .give(players.get(2), Card.espadilla(), Card.basto())
 *     .hands();
 * </pre>
 */
public final class TestDeal {

    public static final int HAND_SIZE = Deck.CARDS_PER_PLAYER;

    private final List<PlayerId> players;
    private final Map<PlayerId, List<Card>> pinned = new LinkedHashMap<>();

    private TestDeal(List<PlayerId> players) {
        this.players = List.copyOf(players);
    }

    public static TestDeal forPlayers(List<PlayerId> players) {
        Objects.requireNonNull(players, "players must not be null");
        if (players.size() != Deck.PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "A deal needs exactly " + Deck.PLAYER_COUNT + " players, got: " + players.size()
            );
        }
        return new TestDeal(players);
    }

    /**
     * Pins the given cards to a player's hand. The rest of that hand is filled automatically.
     */
    public TestDeal give(PlayerId player, Card... cards) {
        Objects.requireNonNull(player, "player must not be null");
        if (!players.contains(player)) {
            throw new IllegalArgumentException("Player not in this deal: " + player);
        }

        final var hand = pinned.computeIfAbsent(player, p -> new ArrayList<>());
        for (final var card : cards) {
            Objects.requireNonNull(card, "card must not be null");
            if (allPinned().contains(card)) {
                throw new IllegalArgumentException("Card pinned twice: " + card);
            }
            hand.add(card);
        }

        if (hand.size() > HAND_SIZE) {
            throw new IllegalArgumentException(
                "Cannot pin more than " + HAND_SIZE + " cards to a player, got: " + hand.size()
            );
        }
        return this;
    }

    /**
     * Materialises the deal: every player holds exactly 8 cards and all 40 cards are dealt.
     */
    public Map<PlayerId, List<Card>> hands() {
        final var assigned = allPinned();
        final var remaining = new ArrayList<Card>();
        for (final var card : Deck.create().cards()) {
            if (!assigned.contains(card)) {
                remaining.add(card);
            }
        }

        final var hands = new LinkedHashMap<PlayerId, List<Card>>();
        var next = 0;
        for (final var player : players) {
            final var hand = new ArrayList<>(pinned.getOrDefault(player, List.of()));
            while (hand.size() < HAND_SIZE) {
                hand.add(remaining.get(next++));
            }
            hands.put(player, List.copyOf(hand));
        }
        return Map.copyOf(hands);
    }

    private HashSet<Card> allPinned() {
        final var all = new HashSet<Card>();
        pinned.values().forEach(all::addAll);
        return all;
    }
}
