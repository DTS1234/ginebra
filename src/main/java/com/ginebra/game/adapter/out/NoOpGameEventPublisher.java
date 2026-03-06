package com.ginebra.game.adapter.out;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default no-op event publisher used before the STOMP publisher is configured.
 * Will be replaced by StompGameEventPublisher in Step 4.
 */
@Component
@ConditionalOnMissingBean(name = "stompGameEventPublisher")
public class NoOpGameEventPublisher implements GameEventPublisher {

    @Override
    public void publishToGame(GameId gameId, GameEvent event) {
        // No-op until STOMP publisher is wired
    }

    @Override
    public void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event) {
        // No-op until STOMP publisher is wired
    }
}
