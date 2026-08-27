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
 * In memory, like everything else until persistence lands. Entries are a UUID each and
 * are never removed, which is fine for a play-test and wants revisiting alongside
 * Phase 5.
 */
@Component
public class BotRoster {

    private final Set<PlayerId> bots = ConcurrentHashMap.newKeySet();

    public void register(PlayerId playerId) {
        bots.add(Objects.requireNonNull(playerId, "playerId must not be null"));
    }

    public boolean isBot(PlayerId playerId) {
        return playerId != null && bots.contains(playerId);
    }
}
