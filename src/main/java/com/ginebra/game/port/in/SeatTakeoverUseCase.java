package com.ginebra.game.port.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

/**
 * Hands a seat to a bot when the person in it has gone, and back when they return.
 *
 * There is no reconnection and no persistence yet, so someone closing their tab used to
 * end the table for the other four: nothing anywhere would ever take their turn. Rather
 * than void a hand people are halfway through, the seat gets played - badly, by the same
 * bot that fills empty chairs - and the coins land where they land. It is their hand
 * throughout: come back and it is yours again, mid-basa if need be.
 */
public interface SeatTakeoverUseCase {

    /** @return true if a bot now has the seat and the game needs nudging */
    boolean takeOverSeat(GameId gameId, PlayerId playerId);

    /** @return true if the seat was being played by a bot and has been given back */
    boolean returnSeat(GameId gameId, PlayerId playerId);
}
