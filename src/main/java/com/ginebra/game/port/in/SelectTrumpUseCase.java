package com.ginebra.game.port.in;

import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.Objects;

public interface SelectTrumpUseCase {

    SelectTrumpResult selectTrump(SelectTrumpCommand command);

    record SelectTrumpCommand(GameId gameId, PlayerId playerId, Suit suit) {
        public SelectTrumpCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(suit, "suit must not be null");
        }
    }

    sealed interface SelectTrumpResult {
        record Success() implements SelectTrumpResult {}
        record NotYourTurn() implements SelectTrumpResult {}
        record InvalidGameState(String message) implements SelectTrumpResult {}
        record GameNotFound() implements SelectTrumpResult {}
    }
}
