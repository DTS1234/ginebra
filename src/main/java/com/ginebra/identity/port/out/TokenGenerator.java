package com.ginebra.identity.port.out;

import com.ginebra.identity.domain.PlayerIdentity;

public interface TokenGenerator {
    String generateToken(PlayerIdentity identity);
}
