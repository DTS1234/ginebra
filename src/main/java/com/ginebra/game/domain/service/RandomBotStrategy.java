package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Suit;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The simplest opponent there is: it plays a legal card at random and never bets.
 *
 * The split is deliberate. Among the cards the rules allow it picks uniformly, which is
 * what makes it useful for testing - over enough hands it will eventually try every shape
 * of play the validator has to cope with. But it declines both wagers outright, because a
 * random Soledad or a random "fer todo" is not a dumb player, it is a broken table: one
 * hand in two would be someone going alone on nothing, and a play-test would never see a
 * normal round.
 *
 * It is meant to be replaced. {@link BotStrategy} is the seam.
 */
public class RandomBotStrategy implements BotStrategy {

    private static final Suit[] SUITS = Suit.values();

    private final Random random;

    public RandomBotStrategy(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public Card chooseCard(BotView view, List<Card> legalCards) {
        Objects.requireNonNull(legalCards, "legalCards must not be null");
        if (legalCards.isEmpty()) {
            throw new IllegalArgumentException("A hand always has a legal card to play");
        }
        return legalCards.get(random.nextInt(legalCards.size()));
    }

    @Override
    public Suit chooseTrump(BotView view) {
        return SUITS[random.nextInt(SUITS.length)];
    }

    @Override
    public boolean declaresSoledad(BotView view) {
        return false;
    }

    @Override
    public boolean callsTodo(BotView view) {
        return false;
    }
}
