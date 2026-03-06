package com.ginebra.lobby.port.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.List;

public interface GameStarter {

    GameStartResult startGame(List<PlayerId> players);

    sealed interface GameStartResult {
        record Success(GameId gameId) implements GameStartResult {}
        record Failure(String reason) implements GameStartResult {}
    }
}
