package com.ginebra.connection.adapter;

import com.ginebra.connection.application.ConnectionTracker;
import com.ginebra.game.adapter.in.dto.GameStateMapper;
import com.ginebra.game.adapter.in.dto.ServerMessage;
import com.ginebra.game.application.GameService;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.in.SeatTakeoverUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Intercepts SUBSCRIBE events to /topic/game/{gameId}.
 * When a player subscribes, validates they belong to the game,
 * registers the connection, takes their seat back off any bot that was playing it, sends
 * personalized game state, and broadcasts PLAYER_CONNECTED.
 */
@Component
public class GameSubscriptionInterceptor {

    private static final Pattern GAME_TOPIC_PATTERN = Pattern.compile("/topic/game/([0-9a-f\\-]+)");

    private final ConnectionTracker connectionTracker;
    private final GameService gameService;
    private final GameEventPublisher gameEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameStateMapper gameStateMapper;
    private final SeatTakeoverUseCase seatTakeover;
    private final Clock clock;

    public GameSubscriptionInterceptor(
        ConnectionTracker connectionTracker,
        GameService gameService,
        GameEventPublisher gameEventPublisher,
        SimpMessagingTemplate messagingTemplate,
        GameStateMapper gameStateMapper,
        SeatTakeoverUseCase seatTakeover,
        Clock clock
    ) {
        this.connectionTracker = Objects.requireNonNull(connectionTracker);
        this.gameService = Objects.requireNonNull(gameService);
        this.gameEventPublisher = Objects.requireNonNull(gameEventPublisher);
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
        this.gameStateMapper = Objects.requireNonNull(gameStateMapper);
        this.seatTakeover = Objects.requireNonNull(seatTakeover);
        this.clock = Objects.requireNonNull(clock);
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        final var message = event.getMessage();
        final var headers = message.getHeaders();

        final var destination = (String) headers.get("simpDestination");
        if (destination == null) {
            return;
        }

        final var matcher = GAME_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        final var gameIdStr = matcher.group(1);
        final var gameId = new GameId(UUID.fromString(gameIdStr));
        final var user = event.getUser();
        if (!(user instanceof PlayerAuthentication auth)) {
            return;
        }

        final var playerId = auth.getPlayerIdentity().playerId();
        final var sessionId = (String) headers.get("simpSessionId");

        // Validate player belongs to this game
        final var gameOpt = gameService.getGame(gameId);
        if (gameOpt.isEmpty() || !gameOpt.get().players().contains(playerId)) {
            return;
        }

        // Register connection
        connectionTracker.playerConnected(playerId, gameId, sessionId, clock.instant());

        // If they were away long enough for a bot to take the seat, it is theirs again.
        seatTakeover.returnSeat(gameId, playerId);

        // Send personalized game state to this player
        final var game = gameOpt.get();
        final var gameState = gameStateMapper.toGameState(game, playerId);
        messagingTemplate.convertAndSendToUser(
            playerId.value().toString(),
            "/queue/game-state",
            new ServerMessage("GAME_STATE", gameState)
        );

        // Broadcast player connected to all players in the game
        gameEventPublisher.publishToGame(gameId, new GameEvent.PlayerConnected(playerId));
    }
}
