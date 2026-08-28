package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;

import java.util.List;
import java.util.Objects;

/**
 * How a seat with nobody behind it decides.
 *
 * Four decisions come up in a round, and they are not the same kind of thing. Playing a
 * card and naming trumps are <em>moves</em>: something has to be chosen, and every option
 * is legal. Going alone and calling "fer todo" are <em>wagers</em>: nothing forces them,
 * and getting them wrong costs coins.
 *
 * Implementations get the whole round, not a summary of it. A stronger opponent will want
 * to know what has been led, who is on which side and how the basas are running, and
 * there is no point guessing now which of that it will ask for.
 */
public interface BotStrategy {

    /**
     * Which card to play, from those the rules allow.
     *
     * @param view the round and which seat is deciding
     * @param legalCards what may be played, never empty
     * @return one of {@code legalCards}
     */
    Card chooseCard(BotView view, List<Card> legalCards);

    /** Which suit to make trump, having been dealt the lead. */
    Suit chooseTrump(BotView view);

    /** Whether to go alone against the other four. */
    boolean declaresSoledad(BotView view);

    /** Whether to play on for all eight basas rather than bank the five. */
    boolean callsTodo(BotView view);

    /**
     * Their own king having been forced out of them: whether to carry on alone for 4
     * either way, or stop and pay 1.
     */
    boolean carriesOnAfterKingFell(BotView view);

    /** The round as it stands, and which seat is being asked. */
    record BotView(Round round, PlayerId self) {

        public BotView {
            Objects.requireNonNull(round, "round must not be null");
            Objects.requireNonNull(self, "self must not be null");
        }

        public List<Card> hand() {
            return round.getHand(self);
        }
    }
}
