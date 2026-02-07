package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamsTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateWithCorrectTeamSizes() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThat(teams.teamOfTwo()).hasSize(2);
            assertThat(teams.teamOfThree()).hasSize(3);
        }

        @Test
        void shouldRejectNullTeamOfTwo() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();

            assertThatThrownBy(() -> new Teams(null, Set.of(player1, player2, player3)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("teamOfTwo must not be null");
        }

        @Test
        void shouldRejectNullTeamOfThree() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();

            assertThatThrownBy(() -> new Teams(Set.of(player1, player2), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("teamOfThree must not be null");
        }

        @Test
        void shouldRejectWrongTeamOfTwoSize() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();

            assertThatThrownBy(() -> new Teams(
                Set.of(player1),
                Set.of(player2, player3, player4)
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamOfTwo must have 2 players");
        }

        @Test
        void shouldRejectWrongTeamOfThreeSize() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();

            assertThatThrownBy(() -> new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4)
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamOfThree must have 3 players");
        }

        @Test
        void shouldRejectPlayerOnBothTeams() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();

            assertThatThrownBy(() -> new Teams(
                Set.of(player1, player2),
                Set.of(player2, player3, player4)
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player cannot be on both teams");
        }
    }

    @Nested
    class FactoryMethod {

        @Test
        void shouldCreateFromPlayerWhoGoesAndKingPlayer() {
            final var playerWhoGoes = PlayerId.generate();
            final var kingPlayer = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();
            final var allPlayers = Set.of(playerWhoGoes, kingPlayer, player3, player4, player5);

            final var teams = Teams.of(playerWhoGoes, kingPlayer, allPlayers);

            assertThat(teams.teamOfTwo()).containsExactlyInAnyOrder(playerWhoGoes, kingPlayer);
            assertThat(teams.teamOfThree()).containsExactlyInAnyOrder(player3, player4, player5);
        }

        @Test
        void shouldRejectWhenPlayerWhoGoesPlaysKing() {
            final var playerWhoGoes = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();
            final var allPlayers = Set.of(playerWhoGoes, player2, player3, player4, player5);

            // When playerWhoGoes plays the first King, they're on team with themselves
            // This creates a team of 1, which is invalid
            // In the real game, they can choose to stop the game
            assertThatThrownBy(() -> Teams.of(playerWhoGoes, playerWhoGoes, allPlayers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamOfTwo must have 2 players");
        }

        @Test
        void shouldRejectWhenNotFivePlayers() {
            final var playerWhoGoes = PlayerId.generate();
            final var kingPlayer = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var allPlayers = Set.of(playerWhoGoes, kingPlayer, player3);

            assertThatThrownBy(() -> Teams.of(playerWhoGoes, kingPlayer, allPlayers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must have exactly 5 players");
        }

        @Test
        void shouldRejectNullPlayerWhoGoes() {
            final var kingPlayer = PlayerId.generate();
            final var allPlayers = createFivePlayers();

            assertThatThrownBy(() -> Teams.of(null, kingPlayer, allPlayers))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerWhoGoes must not be null");
        }

        @Test
        void shouldRejectNullKingPlayer() {
            final var playerWhoGoes = PlayerId.generate();
            final var allPlayers = createFivePlayers();

            assertThatThrownBy(() -> Teams.of(playerWhoGoes, null, allPlayers))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kingPlayer must not be null");
        }

        @Test
        void shouldRejectNullAllPlayers() {
            final var playerWhoGoes = PlayerId.generate();
            final var kingPlayer = PlayerId.generate();

            assertThatThrownBy(() -> Teams.of(playerWhoGoes, kingPlayer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allPlayers must not be null");
        }

        @Test
        void shouldRejectPlayerWhoGoesNotInAllPlayers() {
            final var playerWhoGoes = PlayerId.generate();
            final var kingPlayer = PlayerId.generate();
            final var allPlayers = createFivePlayers();

            assertThatThrownBy(() -> Teams.of(playerWhoGoes, kingPlayer, allPlayers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerWhoGoes must be in allPlayers");
        }

        @Test
        void shouldRejectKingPlayerNotInAllPlayers() {
            final var allPlayers = createFivePlayers();
            final var playerWhoGoes = allPlayers.iterator().next();
            final var kingPlayer = PlayerId.generate();

            assertThatThrownBy(() -> Teams.of(playerWhoGoes, kingPlayer, allPlayers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kingPlayer must be in allPlayers");
        }
    }

    @Nested
    class QueryMethods {

        @Test
        void isOnTeamOfTwoShouldReturnTrueForTeamMembers() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThat(teams.isOnTeamOfTwo(player1)).isTrue();
            assertThat(teams.isOnTeamOfTwo(player2)).isTrue();
        }

        @Test
        void isOnTeamOfTwoShouldReturnFalseForNonMembers() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThat(teams.isOnTeamOfTwo(player3)).isFalse();
            assertThat(teams.isOnTeamOfTwo(player4)).isFalse();
            assertThat(teams.isOnTeamOfTwo(player5)).isFalse();
        }

        @Test
        void isOnTeamOfThreeShouldReturnTrueForTeamMembers() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThat(teams.isOnTeamOfThree(player3)).isTrue();
            assertThat(teams.isOnTeamOfThree(player4)).isTrue();
            assertThat(teams.isOnTeamOfThree(player5)).isTrue();
        }

        @Test
        void isOnTeamOfThreeShouldReturnFalseForNonMembers() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThat(teams.isOnTeamOfThree(player1)).isFalse();
            assertThat(teams.isOnTeamOfThree(player2)).isFalse();
        }

        @Test
        void getTeamForShouldReturnCorrectTeam() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();
            final var teamOfTwo = Set.of(player1, player2);
            final var teamOfThree = Set.of(player3, player4, player5);

            final var teams = new Teams(teamOfTwo, teamOfThree);

            assertThat(teams.getTeamFor(player1)).isEqualTo(teamOfTwo);
            assertThat(teams.getTeamFor(player2)).isEqualTo(teamOfTwo);
            assertThat(teams.getTeamFor(player3)).isEqualTo(teamOfThree);
            assertThat(teams.getTeamFor(player4)).isEqualTo(teamOfThree);
            assertThat(teams.getTeamFor(player5)).isEqualTo(teamOfThree);
        }

        @Test
        void getTeamForShouldThrowForUnknownPlayer() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();
            final var unknownPlayer = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThatThrownBy(() -> teams.getTeamFor(unknownPlayer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player not found in any team");
        }

        @Test
        void shouldRejectNullPlayerIdInQueries() {
            final var teams = new Teams(
                Set.of(PlayerId.generate(), PlayerId.generate()),
                Set.of(PlayerId.generate(), PlayerId.generate(), PlayerId.generate())
            );

            assertThatThrownBy(() -> teams.isOnTeamOfTwo(null))
                .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> teams.isOnTeamOfThree(null))
                .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> teams.getTeamFor(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Immutability {

        @Test
        void teamsShouldBeImmutable() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            final var teams = new Teams(
                Set.of(player1, player2),
                Set.of(player3, player4, player5)
            );

            assertThatThrownBy(() -> teams.teamOfTwo().add(PlayerId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);

            assertThatThrownBy(() -> teams.teamOfThree().add(PlayerId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // === Helper Methods ===

    private Set<PlayerId> createFivePlayers() {
        return Set.of(
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate()
        );
    }
}
