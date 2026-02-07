package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.Objects;
import java.util.Set;

/**
 * Result of a completed round.
 */
public sealed interface RoundResult {

    /**
     * A team won the round by reaching 5 basas.
     */
    record Win(Set<PlayerId> winners) implements RoundResult {
        public Win {
            Objects.requireNonNull(winners, "winners must not be null");
            if (winners.isEmpty()) {
                throw new IllegalArgumentException("winners must not be empty");
            }
            winners = Set.copyOf(winners);
        }
    }

    /**
     * Round ended in a draw (4-4 split after 8 basas).
     */
    record Draw() implements RoundResult {
    }
}
