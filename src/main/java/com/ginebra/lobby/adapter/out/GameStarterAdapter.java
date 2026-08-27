package com.ginebra.lobby.adapter.out;

import com.ginebra.game.application.BotTurnDriver;
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
    private final BotTurnDriver botTurnDriver;

    public GameStarterAdapter(StartGameUseCase startGameUseCase, BotTurnDriver botTurnDriver) {
        this.startGameUseCase = Objects.requireNonNull(startGameUseCase);
        this.botTurnDriver = Objects.requireNonNull(botTurnDriver);
    }

    @Override
    public GameStartResult startGame(List<PlayerId> players) {
        final var gameId = GameId.generate();
        final var result = startGameUseCase.startGame(
            new StartGameUseCase.StartGameCommand(gameId, players)
        );

        if (result instanceof StartGameUseCase.StartGameResult.Success success) {
            // The Soledad window is open from the deal, so any bots at the table have
            // something to answer before the first human is asked for anything.
            botTurnDriver.drive(success.gameId());
            return new GameStartResult.Success(success.gameId());
        } else {
            return new GameStartResult.Failure("Game already exists");
        }
    }
}
