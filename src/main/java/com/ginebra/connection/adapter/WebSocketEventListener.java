package com.ginebra.connection.adapter;

import com.ginebra.connection.application.ConnectionTracker;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.out.GameEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Objects;

/**
 * Listens for WebSocket session events and updates the ConnectionTracker.
 */
@Component
public class WebSocketEventListener {

    private final ConnectionTracker connectionTracker;
    private final GameEventPublisher gameEventPublisher;

    public WebSocketEventListener(
        ConnectionTracker connectionTracker,
        GameEventPublisher gameEventPublisher
    ) {
        this.connectionTracker = Objects.requireNonNull(connectionTracker);
        this.gameEventPublisher = Objects.requireNonNull(gameEventPublisher);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        final var sessionId = event.getSessionId();
        final var connectionOpt = connectionTracker.playerDisconnected(sessionId);

        connectionOpt.ifPresent(connection -> {
            gameEventPublisher.publishToGame(
                connection.gameId(),
                new GameEvent.PlayerDisconnected(connection.playerId())
            );
        });
    }
}
