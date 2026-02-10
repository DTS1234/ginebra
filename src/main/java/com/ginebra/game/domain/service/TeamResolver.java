package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Teams;
import com.ginebra.identity.domain.PlayerId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Detects when team formation should occur during a round.
 * Teams are formed when the first King is played:
 * - Player who "goes" + player who played the first King = team of two
 * - Remaining 3 players = team of three
 *
 * Special case: If the player who "goes" plays the first King themselves,
 * they can choose to stop the game. This case is deferred and returns empty.
 */
public class TeamResolver {

    /**
     * Checks if a played card triggers team formation.
     *
     * @param card the card that was just played
     * @param playerId the player who played the card
     * @param playerWhoGoes the player who started the round and chose trump
     * @param allPlayers all 5 players in the round
     * @return the formed teams if this card triggers formation, empty otherwise
     */
    public Optional<Teams> resolveTeams(
        Card card,
        PlayerId playerId,
        PlayerId playerWhoGoes,
        Set<PlayerId> allPlayers
    ) {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(playerWhoGoes, "playerWhoGoes must not be null");
        Objects.requireNonNull(allPlayers, "allPlayers must not be null");

        if (!card.isKing()) {
            return Optional.empty();
        }

        // Special case: player who "goes" plays the first King themselves.
        // Per spec, they can choose to stop the game. Deferred for now.
        if (playerId.equals(playerWhoGoes)) {
            return Optional.empty();
        }

        return Optional.of(Teams.of(playerWhoGoes, playerId, allPlayers));
    }
}
