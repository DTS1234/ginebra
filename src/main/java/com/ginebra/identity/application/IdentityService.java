package com.ginebra.identity.application;

import com.ginebra.identity.port.in.GetCurrentPlayerUseCase;
import com.ginebra.identity.port.out.TokenParser;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class IdentityService implements GetCurrentPlayerUseCase {

    private final TokenParser tokenParser;

    public IdentityService(TokenParser tokenParser) {
        this.tokenParser = Objects.requireNonNull(tokenParser, "tokenParser must not be null");
    }

    @Override
    public GetCurrentPlayerResponse getCurrentPlayer(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token must not be blank");
        }

        final var identity = tokenParser.parseToken(token)
            .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        return new GetCurrentPlayerResponse(
            identity.playerId().toString(),
            identity.displayName(),
            identity.anonymous()
        );
    }
}
