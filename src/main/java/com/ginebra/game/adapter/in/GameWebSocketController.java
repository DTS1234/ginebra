package com.ginebra.game.adapter.in;

import com.ginebra.game.adapter.in.dto.GameStateMapper;
import com.ginebra.game.adapter.in.dto.PlayCardRequest;
import com.ginebra.game.adapter.in.dto.SelectTrumpRequest;
import com.ginebra.game.adapter.in.dto.ServerMessage;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.SoledadUseCase;
import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

@Controller
public class GameWebSocketController {

    private final PlayCardUseCase playCardUseCase;
    private final SelectTrumpUseCase selectTrumpUseCase;
    private final SoledadUseCase soledadUseCase;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameStateMapper gameStateMapper;

    public GameWebSocketController(
        PlayCardUseCase playCardUseCase,
        SelectTrumpUseCase selectTrumpUseCase,
        SoledadUseCase soledadUseCase,
        SimpMessagingTemplate messagingTemplate,
        GameStateMapper gameStateMapper
    ) {
        this.playCardUseCase = Objects.requireNonNull(playCardUseCase);
        this.selectTrumpUseCase = Objects.requireNonNull(selectTrumpUseCase);
        this.soledadUseCase = Objects.requireNonNull(soledadUseCase);
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
        this.gameStateMapper = Objects.requireNonNull(gameStateMapper);
    }

    @MessageMapping("/game/{gameId}/play-card")
    public void playCard(
        @DestinationVariable String gameId,
        @Payload PlayCardRequest request,
        Principal principal
    ) {
        final var playerId = extractPlayerId(principal);
        final var parsedGameId = new GameId(UUID.fromString(gameId));
        final var card = parseCard(request.card());

        final var result = playCardUseCase.playCard(
            new PlayCardUseCase.PlayCardCommand(parsedGameId, playerId, card)
        );

        if (result instanceof PlayCardUseCase.PlayCardResult.NotYourTurn) {
            sendError(playerId, "NOT_YOUR_TURN", "It's not your turn");
        } else if (result instanceof PlayCardUseCase.PlayCardResult.InvalidCard invalid) {
            sendError(playerId, invalid.code(), invalid.message());
        } else if (result instanceof PlayCardUseCase.PlayCardResult.InvalidGameState invalid) {
            sendError(playerId, "INVALID_GAME_STATE", invalid.message());
        } else if (result instanceof PlayCardUseCase.PlayCardResult.GameNotFound) {
            sendError(playerId, "GAME_NOT_FOUND", "Game not found");
        }
    }

    @MessageMapping("/game/{gameId}/select-trump")
    public void selectTrump(
        @DestinationVariable String gameId,
        @Payload SelectTrumpRequest request,
        Principal principal
    ) {
        final var playerId = extractPlayerId(principal);
        final var parsedGameId = new GameId(UUID.fromString(gameId));
        final var suit = Suit.valueOf(request.suit());

        final var result = selectTrumpUseCase.selectTrump(
            new SelectTrumpUseCase.SelectTrumpCommand(parsedGameId, playerId, suit)
        );

        if (result instanceof SelectTrumpUseCase.SelectTrumpResult.NotYourTurn) {
            sendError(playerId, "NOT_YOUR_TURN", "Only the player who goes can select trump");
        } else if (result instanceof SelectTrumpUseCase.SelectTrumpResult.InvalidGameState invalid) {
            sendError(playerId, "INVALID_GAME_STATE", invalid.message());
        } else if (result instanceof SelectTrumpUseCase.SelectTrumpResult.GameNotFound) {
            sendError(playerId, "GAME_NOT_FOUND", "Game not found");
        }
    }

    @MessageMapping("/game/{gameId}/soledad-pass")
    public void soledadPass(
        @DestinationVariable String gameId,
        Principal principal
    ) {
        final var playerId = extractPlayerId(principal);
        final var parsedGameId = new GameId(UUID.fromString(gameId));

        final var result = soledadUseCase.passSoledad(
            new SoledadUseCase.PassSoledadCommand(parsedGameId, playerId)
        );

        if (result instanceof SoledadUseCase.PassSoledadResult.AlreadyPassed) {
            sendError(playerId, "ALREADY_PASSED", "You already passed soledad");
        } else if (result instanceof SoledadUseCase.PassSoledadResult.WindowClosed) {
            sendError(playerId, "WINDOW_CLOSED", "Soledad window is closed");
        } else if (result instanceof SoledadUseCase.PassSoledadResult.InvalidGameState invalid) {
            sendError(playerId, "INVALID_GAME_STATE", invalid.message());
        } else if (result instanceof SoledadUseCase.PassSoledadResult.GameNotFound) {
            sendError(playerId, "GAME_NOT_FOUND", "Game not found");
        }
    }

    @MessageMapping("/game/{gameId}/declare-soledad")
    public void declareSoledad(
        @DestinationVariable String gameId,
        Principal principal
    ) {
        final var playerId = extractPlayerId(principal);
        final var parsedGameId = new GameId(UUID.fromString(gameId));

        final var result = soledadUseCase.declareSoledad(
            new SoledadUseCase.DeclareSoledadCommand(parsedGameId, playerId)
        );

        if (result instanceof SoledadUseCase.DeclareSoledadResult.WindowClosed) {
            sendError(playerId, "WINDOW_CLOSED", "Soledad window is closed");
        } else if (result instanceof SoledadUseCase.DeclareSoledadResult.InvalidGameState invalid) {
            sendError(playerId, "INVALID_GAME_STATE", invalid.message());
        } else if (result instanceof SoledadUseCase.DeclareSoledadResult.GameNotFound) {
            sendError(playerId, "GAME_NOT_FOUND", "Game not found");
        }
    }

    private PlayerId extractPlayerId(Principal principal) {
        if (principal instanceof PlayerAuthentication auth) {
            return auth.getPlayerIdentity().playerId();
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass().getSimpleName());
    }

    private Card parseCard(com.ginebra.game.adapter.in.dto.CardDto cardDto) {
        return new Card(Suit.valueOf(cardDto.suit()), Rank.valueOf(cardDto.rank()));
    }

    private void sendError(PlayerId playerId, String code, String message) {
        final var errorEvent = new GameEvent.GameError(code, message);
        final var serverMessage = gameStateMapper.toServerMessage(errorEvent);
        messagingTemplate.convertAndSendToUser(
            playerId.value().toString(),
            "/queue/errors",
            serverMessage
        );
    }
}
