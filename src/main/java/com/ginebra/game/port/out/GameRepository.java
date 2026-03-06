package com.ginebra.game.port.out;

import com.ginebra.game.domain.model.Game;
import com.ginebra.lobby.domain.GameId;

import java.util.Optional;

public interface GameRepository {
    void save(Game game);
    Optional<Game> findById(GameId gameId);
    void delete(GameId gameId);
}
