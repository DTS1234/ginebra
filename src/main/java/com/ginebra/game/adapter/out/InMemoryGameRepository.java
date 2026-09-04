package com.ginebra.game.adapter.out;

import com.ginebra.game.domain.model.Game;
import com.ginebra.game.port.out.GameRepository;
import com.ginebra.lobby.domain.GameId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryGameRepository implements GameRepository {

    private final ConcurrentHashMap<GameId, Game> games = new ConcurrentHashMap<>();

    @Override
    public void save(Game game) {
        games.put(game.gameId(), game);
    }

    @Override
    public Optional<Game> findById(GameId gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    @Override
    public List<Game> findAll() {
        return List.copyOf(games.values());
    }

    @Override
    public void delete(GameId gameId) {
        games.remove(gameId);
    }

    // Test helpers
    public void clear() {
        games.clear();
    }

    public int size() {
        return games.size();
    }
}
