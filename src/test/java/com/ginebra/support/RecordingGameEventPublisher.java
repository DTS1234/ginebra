package com.ginebra.support;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-written test double that records everything the game publishes,
 * so tests can assert on the event stream instead of mocking the port.
 */
public final class RecordingGameEventPublisher implements GameEventPublisher {

    private final List<GameEvent> gameEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<PlayerEvent> playerEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publishToGame(GameId gameId, GameEvent event) {
        gameEvents.add(event);
    }

    @Override
    public void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event) {
        playerEvents.add(new PlayerEvent(playerId, event));
    }

    public List<GameEvent> gameEvents() {
        return List.copyOf(gameEvents);
    }

    public List<PlayerEvent> playerEvents() {
        return List.copyOf(playerEvents);
    }

    /**
     * Returns every broadcast event of the given type, in publication order.
     */
    public <T extends GameEvent> List<T> eventsOfType(Class<T> type) {
        return gameEvents().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    /**
     * Returns the first broadcast event of the given type.
     */
    public <T extends GameEvent> T firstEventOfType(Class<T> type) {
        return eventsOfType(type).stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("No event published of type " + type.getSimpleName()));
    }

    public void clear() {
        gameEvents.clear();
        playerEvents.clear();
    }

    public record PlayerEvent(PlayerId playerId, GameEvent event) {}
}
