package com.ginebra.game.adapter.out;

import com.ginebra.game.adapter.in.dto.GameStateMapper;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Publishes game events over STOMP topics.
 * Broadcast events go to /topic/game/{gameId}, player-specific events go to /user/{playerId}/queue/errors.
 */
@Component("stompGameEventPublisher")
public class StompGameEventPublisher implements GameEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameStateMapper gameStateMapper;

    public StompGameEventPublisher(
        SimpMessagingTemplate messagingTemplate,
        GameStateMapper gameStateMapper
    ) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
        this.gameStateMapper = Objects.requireNonNull(gameStateMapper);
    }

    @Override
    public void publishToGame(GameId gameId, GameEvent event) {
        final var message = gameStateMapper.toServerMessage(event);
        messagingTemplate.convertAndSend(
            "/topic/game/" + gameId.value().toString(),
            message
        );
    }

    @Override
    public void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event) {
        final var message = gameStateMapper.toServerMessage(event);
        messagingTemplate.convertAndSendToUser(
            playerId.value().toString(),
            "/queue/errors",
            message
        );
    }
}
