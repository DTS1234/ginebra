package com.ginebra.game.port.in;

import com.ginebra.game.domain.model.Card;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.Objects;

public interface PlayCardUseCase {

    PlayCardResult playCard(PlayCardCommand command);

    record PlayCardCommand(GameId gameId, PlayerId playerId, Card card) {
        public PlayCardCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(card, "card must not be null");
        }
    }

    sealed interface PlayCardResult {
        record Success() implements PlayCardResult {}
        record NotYourTurn() implements PlayCardResult {}
        record InvalidCard(String code, String message) implements PlayCardResult {}
        record InvalidGameState(String message) implements PlayCardResult {}
        record GameNotFound() implements PlayCardResult {}
    }
}
