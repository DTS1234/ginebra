package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the team assignment in a round.
 * Team of two: the player who goes + the player who plays the first King.
 * Team of three: the remaining 3 players.
 */
public record Teams(
    Set<PlayerId> teamOfTwo,
    Set<PlayerId> teamOfThree
) {

    public static final int TEAM_OF_TWO_SIZE = 2;
    public static final int TEAM_OF_THREE_SIZE = 3;
    public static final int TOTAL_PLAYERS = 5;

    public Teams {
        Objects.requireNonNull(teamOfTwo, "teamOfTwo must not be null");
        Objects.requireNonNull(teamOfThree, "teamOfThree must not be null");

        if (teamOfTwo.size() != TEAM_OF_TWO_SIZE) {
            throw new IllegalArgumentException(
                "teamOfTwo must have " + TEAM_OF_TWO_SIZE + " players, got: " + teamOfTwo.size()
            );
        }
        if (teamOfThree.size() != TEAM_OF_THREE_SIZE) {
            throw new IllegalArgumentException(
                "teamOfThree must have " + TEAM_OF_THREE_SIZE + " players, got: " + teamOfThree.size()
            );
        }

        for (final var player : teamOfTwo) {
            if (teamOfThree.contains(player)) {
                throw new IllegalArgumentException("Player cannot be on both teams: " + player);
            }
        }

        teamOfTwo = Set.copyOf(teamOfTwo);
        teamOfThree = Set.copyOf(teamOfThree);
    }

    /**
     * Creates teams from the player who goes and the player who played the first King.
     *
     * @param playerWhoGoes the player who started the round and chose trump
     * @param kingPlayer the player who played the first King
     * @param allPlayers all 5 players in the round
     * @return the team assignment
     */
    public static Teams of(PlayerId playerWhoGoes, PlayerId kingPlayer, Set<PlayerId> allPlayers) {
        Objects.requireNonNull(playerWhoGoes, "playerWhoGoes must not be null");
        Objects.requireNonNull(kingPlayer, "kingPlayer must not be null");
        Objects.requireNonNull(allPlayers, "allPlayers must not be null");

        if (allPlayers.size() != TOTAL_PLAYERS) {
            throw new IllegalArgumentException(
                "Must have exactly " + TOTAL_PLAYERS + " players, got: " + allPlayers.size()
            );
        }

        if (!allPlayers.contains(playerWhoGoes)) {
            throw new IllegalArgumentException("playerWhoGoes must be in allPlayers");
        }

        if (!allPlayers.contains(kingPlayer)) {
            throw new IllegalArgumentException("kingPlayer must be in allPlayers");
        }

        final var teamOfTwo = Set.of(playerWhoGoes, kingPlayer);
        final var teamOfThree = allPlayers.stream()
            .filter(p -> !teamOfTwo.contains(p))
            .collect(Collectors.toUnmodifiableSet());

        return new Teams(teamOfTwo, teamOfThree);
    }

    /**
     * Checks if a player is on the team of two.
     */
    public boolean isOnTeamOfTwo(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return teamOfTwo.contains(playerId);
    }

    /**
     * Checks if a player is on the team of three.
     */
    public boolean isOnTeamOfThree(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return teamOfThree.contains(playerId);
    }

    /**
     * Returns the team that the player belongs to.
     *
     * @throws IllegalArgumentException if player is not found in any team
     */
    public Set<PlayerId> getTeamFor(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (teamOfTwo.contains(playerId)) {
            return teamOfTwo;
        }
        if (teamOfThree.contains(playerId)) {
            return teamOfThree;
        }
        throw new IllegalArgumentException("Player not found in any team: " + playerId);
    }
}
