package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What one round moves between the players and the posso, line by line.
 *
 * A player's entry is positive when they collect from the pot and negative when they pay
 * into it. The two sides do not balance and are not meant to: the pot absorbs the
 * difference, which is what it is for (rules-source.md §3).
 *
 * The lines are the settlement, not a commentary on it - {@link #playerDeltas()} is their
 * sum. Nobody at a real table hands over two coins without saying what for, and the same
 * goes here.
 *
 * @param charges what each player was charged or paid, in the order it was worked out
 */
public record Settlement(Map<PlayerId, List<Charge>> charges) {

    public Settlement {
        Objects.requireNonNull(charges, "charges must not be null");
        charges = charges.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, e -> List.copyOf(e.getValue())
        ));
    }

    /** One line: what it was for, and what it moved. Negative is paid into the pot. */
    public record Charge(Reason reason, int amount) {

        public Charge {
            Objects.requireNonNull(reason, "reason must not be null");
        }

        /**
         * Why a coin moved. The three bases are mutually exclusive and signed by the
         * result; so are the two increments. The rest stand on their own.
         */
        public enum Reason {
            /** A king was put on you: the pair's stake. */
            HELPED,
            /** You carried on alone after your own king was forced out. */
            SELF_KING,
            /** You went alone. */
            SOLEDAD,
            /** The first four basas, in a row, by the going side. */
            PRIMERES,
            /** Espadilla, manilla and basto, held between the going side. */
            ESTUTXE,
            /** Espadilla and basto in one hand. Collected whatever happened. */
            DENGUE,
            /** All four kings dealt to one player. */
            FOUR_KINGS,
            /** All eight basas, called at five and made. */
            TODO_MADE,
            /** Called at five and missed. */
            TODO_MISSED,
            /** The going side fell short of five, and you were against them. */
            HELD_THEM_OFF,
            /** Your own king fell and you stopped the hand rather than play it out. */
            STOPPED
        }
    }

    /** Per-player change, positive to collect and negative to pay. */
    public Map<PlayerId, Integer> playerDeltas() {
        return charges.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            e -> e.getValue().stream().mapToInt(Charge::amount).sum()
        ));
    }

    /**
     * The net change to the posso: the mirror of what the players take out and put in.
     * Negative when the pot pays out more than it takes.
     */
    public int possoDelta() {
        return -playerDeltas().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The total the pot must be able to cover for this settlement to be payable.
     */
    public int totalCollected() {
        return playerDeltas().values().stream().filter(v -> v > 0).mapToInt(Integer::intValue).sum();
    }
}
