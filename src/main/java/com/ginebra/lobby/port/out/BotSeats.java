package com.ginebra.lobby.port.out;

import com.ginebra.identity.domain.PlayerId;

import java.util.List;
import java.util.Objects;

/**
 * Where the lobby gets players that are not people.
 *
 * The lobby's job is seats: it knows a room needs five and how many are empty. It has no
 * business knowing what makes a bot a bot, so it asks for as many as it is short of and
 * seats whatever comes back like any other player.
 */
public interface BotSeats {

    /**
     * Makes {@code count} bot players, each with an id and a name, and records them as
     * bots so their turns will be taken for them.
     */
    List<BotSeat> create(int count);

    record BotSeat(PlayerId playerId, String displayName) {

        public BotSeat {
            Objects.requireNonNull(playerId, "playerId must not be null");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }
}
