package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamResolverTest {

    private final TeamResolver resolver = new TeamResolver();

    private static final PlayerId GOES = PlayerId.generate();
    private static final PlayerId P2 = PlayerId.generate();
    private static final PlayerId P3 = PlayerId.generate();
    private static final PlayerId P4 = PlayerId.generate();
    private static final PlayerId P5 = PlayerId.generate();
    private static final Set<PlayerId> ALL_PLAYERS = Set.of(GOES, P2, P3, P4, P5);

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    @Nested
    class NoTeamFormation {

        @Test
        void shouldReturnEmptyForNonKingCard() {
            final var result = resolver.resolveTeams(
                card(Suit.COPAS, Rank.CABALLO), P2, GOES, ALL_PLAYERS
            );

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForAce() {
            final var result = resolver.resolveTeams(
                card(Suit.COPAS, Rank.AS), P2, GOES, ALL_PLAYERS
            );

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForEspadilla() {
            final var result = resolver.resolveTeams(
                Card.espadilla(), P2, GOES, ALL_PLAYERS
            );

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForBasto() {
            final var result = resolver.resolveTeams(
                Card.basto(), P2, GOES, ALL_PLAYERS
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class TeamFormation {

        @ParameterizedTest
        @EnumSource(Suit.class)
        void shouldFormTeamsWhenKingPlayedByNonGoesPlayer(Suit suit) {
            final var king = card(suit, Rank.REY);

            final var result = resolver.resolveTeams(king, P3, GOES, ALL_PLAYERS);

            assertThat(result).isPresent();
            final var teams = result.get();
            assertThat(teams.teamOfTwo()).containsExactlyInAnyOrder(GOES, P3);
            assertThat(teams.teamOfThree()).containsExactlyInAnyOrder(P2, P4, P5);
        }

        @Test
        void shouldFormCorrectTeamsWithDifferentKingPlayer() {
            final var king = card(Suit.OROS, Rank.REY);

            final var result = resolver.resolveTeams(king, P5, GOES, ALL_PLAYERS);

            assertThat(result).isPresent();
            final var teams = result.get();
            assertThat(teams.teamOfTwo()).containsExactlyInAnyOrder(GOES, P5);
            assertThat(teams.teamOfThree()).containsExactlyInAnyOrder(P2, P3, P4);
        }

        @Test
        void shouldPlacePlayerWhoGoesOnTeamOfTwo() {
            final var king = card(Suit.COPAS, Rank.REY);

            final var result = resolver.resolveTeams(king, P2, GOES, ALL_PLAYERS);

            assertThat(result).isPresent();
            assertThat(result.get().isOnTeamOfTwo(GOES)).isTrue();
            assertThat(result.get().isOnTeamOfTwo(P2)).isTrue();
        }
    }

    @Nested
    class SelfKing {

        @Test
        void shouldReturnEmptyWhenGoesPlayerPlaysKing() {
            final var king = card(Suit.COPAS, Rank.REY);

            final var result = resolver.resolveTeams(king, GOES, GOES, ALL_PLAYERS);

            // Self-king special case: deferred, returns empty
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void shouldReturnEmptyForAllKingSuitsWhenGoesPlayerPlays(Suit suit) {
            final var king = card(suit, Rank.REY);

            final var result = resolver.resolveTeams(king, GOES, GOES, ALL_PLAYERS);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class NullValidation {

        @Test
        void shouldRejectNullCard() {
            assertThatThrownBy(() -> resolver.resolveTeams(null, P2, GOES, ALL_PLAYERS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card must not be null");
        }

        @Test
        void shouldRejectNullPlayerId() {
            final var king = card(Suit.COPAS, Rank.REY);

            assertThatThrownBy(() -> resolver.resolveTeams(king, null, GOES, ALL_PLAYERS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerId must not be null");
        }

        @Test
        void shouldRejectNullPlayerWhoGoes() {
            final var king = card(Suit.COPAS, Rank.REY);

            assertThatThrownBy(() -> resolver.resolveTeams(king, P2, null, ALL_PLAYERS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerWhoGoes must not be null");
        }

        @Test
        void shouldRejectNullAllPlayers() {
            final var king = card(Suit.COPAS, Rank.REY);

            assertThatThrownBy(() -> resolver.resolveTeams(king, P2, GOES, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allPlayers must not be null");
        }
    }
}
