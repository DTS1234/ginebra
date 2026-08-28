package com.ginebra.game.port.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.util.Objects;

/**
 * What to do when your own king is dragged out of you while you are the one going.
 *
 * <i>"Si te cae el rey cuando vas, puedes elegir si quieres seguir (si tienes cartas
 * buenas para hacer 5) o parar si no lo tienes bueno"</i> (the players, 2026-08-27).
 * Carrying on is one against four, worth 4 either way; stopping costs 1 and ends the hand
 * there, with nobody else paying or collecting.
 *
 * Only a king that was <b>forced</b> asks the question. Putting your own king down when
 * you had another legal card is a decision already taken.
 */
public interface KingChoiceUseCase {

    /**
     * @param carryOn true to play the hand out alone, false to stop and pay 1
     */
    KingChoiceResult decideKingChoice(KingChoiceCommand command, boolean carryOn);

    record KingChoiceCommand(GameId gameId, PlayerId playerId) {
        public KingChoiceCommand {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    sealed interface KingChoiceResult {
        record Success() implements KingChoiceResult {}
        record NotYourCall() implements KingChoiceResult {}
        record GameNotFound() implements KingChoiceResult {}
        record InvalidGameState(String message) implements KingChoiceResult {}
    }
}
