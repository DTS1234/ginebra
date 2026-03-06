package com.ginebra.lobby.adapter.out;

import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.lobby.port.out.GameStarter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GameStarterAdapter implements GameStarter {

    private final StartGameUseCase startGameUseCase;

    public GameStarterAdapter(StartGameUseCase startGameUseCase) {
        this.startGameUseCase = Objects.requireNonNull(startGameUseCase);
    }

    @Override
    public GameStartResult startGame(List<PlayerId> players) {
        final var gameId = GameId.generate();
        final var result = startGameUseCase.startGame(
            new StartGameUseCase.StartGameCommand(gameId, players)
        );

        if (result instanceof StartGameUseCase.StartGameResult.Success success) {
            return new GameStartResult.Success(success.gameId());
        } else {
            return new GameStartResult.Failure("Game already exists");
        }
    }
}
