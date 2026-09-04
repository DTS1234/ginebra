package com.ginebra.game.port.in;

import com.ginebra.lobby.domain.GameId;

import java.time.Instant;
import java.util.List;

/**
 * Closes soledad windows nobody answered.
 *
 * The window is the one moment in a hand where every player has to say something before
 * anyone can move, so one person looking away stops the table dead. Two minutes after the
 * deal (design §8) the rest of them are entitled to get on with it, and silence counts as
 * a pass - the safe reading, since going alone is a thing you do on purpose.
 */
public interface ExpireSoledadUseCase {

    /**
     * Passes for everyone still silent in every window whose time is up.
     *
     * @param sweptAt the moment to judge the deadlines against
     * @return the games that moved on, so the caller can nudge whatever is due next
     */
    List<GameId> expireSoledadWindows(Instant sweptAt);
}
