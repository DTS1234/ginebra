package com.ginebra.game.application;

import com.ginebra.identity.domain.PlayerId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which seats have nobody behind them.
 *
 * A bot is an ordinary player as far as the rest of the game is concerned - it has a
 * player id, it sits in a room, it is dealt a hand, it wins and loses coins. The only
 * thing that marks it out is being in here, which is what tells {@link BotTurnDriver}
 * that its turn will not take itself.
 *
 * In memory, like everything else until persistence lands. A seat dealt as a bot stays
 * one for the life of the process; a human's seat is only in here for as long as they
 * are away from it.
 */
@Component
public class BotRoster {

    private final Set<PlayerId> bots = ConcurrentHashMap.newKeySet();

    public void register(PlayerId playerId) {
        bots.add(Objects.requireNonNull(playerId, "playerId must not be null"));
    }

    /**
     * Gives a seat back to the person who was in it.
     *
     * Only ever called for a human whose seat was taken over while they were gone - a bot
     * that was dealt in as a bot has nobody to hand back to.
     */
    public void release(PlayerId playerId) {
        bots.remove(Objects.requireNonNull(playerId, "playerId must not be null"));
    }

    public boolean isBot(PlayerId playerId) {
        return playerId != null && bots.contains(playerId);
    }
}
