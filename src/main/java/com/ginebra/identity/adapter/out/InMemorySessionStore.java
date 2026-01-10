package com.ginebra.identity.adapter.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.out.SessionRepository;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySessionStore implements SessionRepository {

    private final ConcurrentHashMap<PlayerId, PlayerIdentity> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        sessions.put(identity.playerId(), identity);
    }

    @Override
    public Optional<PlayerIdentity> findById(PlayerId id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public void deleteById(PlayerId id) {
        Objects.requireNonNull(id, "id must not be null");
        sessions.remove(id);
    }

    // Test helper methods
    public void clear() {
        sessions.clear();
    }

    public int size() {
        return sessions.size();
    }
}
