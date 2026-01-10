package com.ginebra.identity.port.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;

import java.util.Optional;

public interface SessionRepository {
    void save(PlayerIdentity identity);
    Optional<PlayerIdentity> findById(PlayerId id);
    void deleteById(PlayerId id);
}
