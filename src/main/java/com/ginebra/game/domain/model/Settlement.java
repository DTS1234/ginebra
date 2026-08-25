package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.Map;
import java.util.Objects;

/**
 * What one round moves between the players and the posso.
 *
 * A player's entry is positive when they collect from the pot and negative when they pay
 * into it. The two sides do not balance and are not meant to: the pot absorbs the
 * difference, which is what it is for (rules-source.md §3).
 *
 * @param playerDeltas per-player change, positive to collect and negative to pay
 */
public record Settlement(Map<PlayerId, Integer> playerDeltas) {

    public Settlement {
        Objects.requireNonNull(playerDeltas, "playerDeltas must not be null");
        playerDeltas = Map.copyOf(playerDeltas);
    }

    /**
     * The net change to the posso: the mirror of what the players take out and put in.
     * Negative when the pot pays out more than it takes.
     */
    public int possoDelta() {
        return -playerDeltas.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The total the pot must be able to cover for this settlement to be payable.
     */
    public int totalCollected() {
        return playerDeltas.values().stream().filter(v -> v > 0).mapToInt(Integer::intValue).sum();
    }
}
