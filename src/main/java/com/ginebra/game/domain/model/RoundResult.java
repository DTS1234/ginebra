package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.Objects;
import java.util.Set;

/**
 * Result of a completed round.
 *
 * There is no draw. The source's outcome is binary - the side that goes makes its 5 basas
 * or it does not (rules-source.md §4.1, §5) - so a 4-4 finish is the going side failing,
 * not a tie. Two of the four cases end the hand before it is played out.
 */
public sealed interface RoundResult {

    /**
     * The side that goes made its 5 basas.
     */
    record GoingSideWon(Set<PlayerId> goingSide, Set<PlayerId> opposingSide, int goingSideBasas)
        implements RoundResult {

        public GoingSideWon {
            Objects.requireNonNull(goingSide, "goingSide must not be null");
            Objects.requireNonNull(opposingSide, "opposingSide must not be null");
            goingSide = Set.copyOf(goingSide);
            opposingSide = Set.copyOf(opposingSide);
        }

        @Override
        public Set<PlayerId> winners() {
            return goingSide;
        }
    }

    /**
     * The side that goes was held under 5 basas. The opposing side wins.
     */
    record GoingSideFailed(Set<PlayerId> goingSide, Set<PlayerId> opposingSide, int goingSideBasas)
        implements RoundResult {

        public GoingSideFailed {
            Objects.requireNonNull(goingSide, "goingSide must not be null");
            Objects.requireNonNull(opposingSide, "opposingSide must not be null");
            goingSide = Set.copyOf(goingSide);
            opposingSide = Set.copyOf(opposingSide);
        }

        @Override
        public Set<PlayerId> winners() {
            return opposingSide;
        }
    }

    /**
     * One player was dealt all four kings: the hand ends before a card is played
     * (rules-source.md §4.8).
     */
    record FourKings(PlayerId holder) implements RoundResult {

        public FourKings {
            Objects.requireNonNull(holder, "holder must not be null");
        }

        @Override
        public Set<PlayerId> winners() {
            return Set.of(holder);
        }
    }

    /**
     * The king of the one who goes was forced out, which ends the hand with no side ever
     * formed: <i>"Si es qui és mà li cau el rei s'acaba sa mà"</i> (rules-source.md §4.3).
     */
    record KingFell(PlayerId playerWhoGoes) implements RoundResult {

        public KingFell {
            Objects.requireNonNull(playerWhoGoes, "playerWhoGoes must not be null");
        }

        @Override
        public Set<PlayerId> winners() {
            return Set.of();
        }
    }

    /**
     * The players who won the round. Empty when the hand ended with no side formed.
     */
    Set<PlayerId> winners();
}
