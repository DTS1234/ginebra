package com.ginebra.game.port.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.Objects;

/**
 * "Fer todo": once the going side reaches five basas having won every one of them, they
 * choose whether to play on for all eight. Making it is worth a coin and missing it costs
 * one, so it is a real gamble and has to be theirs to call (rules-source.md §4.8).
 */
public interface TodoUseCase {

    /**
     * @param call true to go for todo, false to bank the win and end the round
     */
    TodoResult decideTodo(TodoCommand command, boolean call);

    record TodoCommand(GameId gameId, PlayerId playerId) {
        public TodoCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    sealed interface TodoResult {
        record Success() implements TodoResult {}
        record NotYourCall() implements TodoResult {}
        record GameNotFound() implements TodoResult {}
        record InvalidGameState(String message) implements TodoResult {}
    }
}
