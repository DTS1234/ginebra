package com.ginebra.game.port.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.Objects;

public interface SoledadUseCase {

    PassSoledadResult passSoledad(PassSoledadCommand command);

    DeclareSoledadResult declareSoledad(DeclareSoledadCommand command);

    record PassSoledadCommand(GameId gameId, PlayerId playerId) {
        public PassSoledadCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    record DeclareSoledadCommand(GameId gameId, PlayerId playerId) {
        public DeclareSoledadCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    sealed interface PassSoledadResult {
        record Success() implements PassSoledadResult {}
        record AlreadyPassed() implements PassSoledadResult {}
        record WindowClosed() implements PassSoledadResult {}
        record GameNotFound() implements PassSoledadResult {}
        record InvalidGameState(String message) implements PassSoledadResult {}
    }

    sealed interface DeclareSoledadResult {
        record Success() implements DeclareSoledadResult {}
        record WindowClosed() implements DeclareSoledadResult {}
        record GameNotFound() implements DeclareSoledadResult {}
        record InvalidGameState(String message) implements DeclareSoledadResult {}
    }
}
