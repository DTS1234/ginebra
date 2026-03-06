package com.ginebra.game.port.out;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

public interface GameEventPublisher {
    void publishToGame(GameId gameId, GameEvent event);
    void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event);
}
