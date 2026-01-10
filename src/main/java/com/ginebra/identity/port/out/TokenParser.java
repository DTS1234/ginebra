package com.ginebra.identity.port.out;

import com.ginebra.identity.domain.PlayerIdentity;

import java.util.Optional;

public interface TokenParser {
    Optional<PlayerIdentity> parseToken(String token);
}
