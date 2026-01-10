package com.ginebra.identity.port.in;

public interface GetCurrentPlayerUseCase {

    GetCurrentPlayerResponse getCurrentPlayer(String token);

    record GetCurrentPlayerResponse(
        String playerId,
        String displayName,
        boolean anonymous
    ) {
        public GetCurrentPlayerResponse {
            if (playerId == null || playerId.isBlank()) {
                throw new IllegalArgumentException("playerId must not be blank");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }
}
