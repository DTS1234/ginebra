package com.ginebra.identity.port.in;

import java.util.Optional;

public interface CreateAnonymousUseCase {

    CreateAnonymousResponse createAnonymous(CreateAnonymousCommand command);

    record CreateAnonymousCommand(Optional<String> displayName) {
        public CreateAnonymousCommand {
            if (displayName == null) {
                displayName = Optional.empty();
            }
        }
    }

    record CreateAnonymousResponse(
        String token,
        String playerId,
        String displayName
    ) {
        public CreateAnonymousResponse {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token must not be blank");
            }
            if (playerId == null || playerId.isBlank()) {
                throw new IllegalArgumentException("playerId must not be blank");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }
}
