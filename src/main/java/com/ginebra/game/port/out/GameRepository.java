package com.ginebra.game.port.out;

import com.ginebra.game.domain.model.Game;
import com.ginebra.lobby.domain.GameId;

import java.util.List;
import java.util.Optional;

public interface GameRepository {
    void save(Game game);
    Optional<Game> findById(GameId gameId);

    /** Every game still in play. The sweepers need to look at all of them; nothing else does. */
    List<Game> findAll();

    void delete(GameId gameId);
}
