package com.ginebra.game.port.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.List;
import java.util.Objects;

public interface StartGameUseCase {

    StartGameResult startGame(StartGameCommand command);

    record StartGameCommand(GameId gameId, List<PlayerId> players) {
        public StartGameCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(players, "players must not be null");
            players = List.copyOf(players);
        }
    }

    sealed interface StartGameResult {
        record Success(GameId gameId) implements StartGameResult {}
        record GameAlreadyExists() implements StartGameResult {}
    }
}
