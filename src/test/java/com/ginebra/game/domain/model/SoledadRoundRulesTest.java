package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.support.LegalMoves;
import com.ginebra.support.TestDeal;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rules for a Soledad round (spec 2.5 and 2.6): one player declares, chooses trump and
 * plays alone against the other four.
 *
 * The declaration window itself works today and is covered by the enabled tests below.
 * What follows the declaration does not: the round is still resolved as an ordinary
 * 2-vs-3 game. Those tests are disabled and act as the acceptance criteria for the fix -
 * enable them as each behaviour lands.
 */
@DisplayName("Soledad round rules")
class SoledadRoundRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(120);
    private static final Suit TRUMP = Suit.COPAS;

    /** Normal rotation says this seat starts the round. */
    private static final int STARTER_SEAT = 0;
    /** A different seat declares Soledad, so the two roles cannot be confused. */
    private static final int DECLARER_SEAT = 2;

    private final List<PlayerId> players = List.of(
        PlayerId.generate(),
        PlayerId.generate(),
        PlayerId.generate(),
        PlayerId.generate(),
        PlayerId.generate()
    );

    private PlayerId starter() {
        return players.get(STARTER_SEAT);
    }

    private PlayerId declarer() {
        return players.get(DECLARER_SEAT);
    }

    @Nested
    @DisplayName("Declaration window")
    class DeclarationWindow {

        @Test
        void shouldStartRoundWaitingForSoledad() {
            // Act
            final var round = newRound();

            // Assert
            assertThat(round.isWaitingForSoledad()).isTrue();
            assertThat(round.soledadPasses()).isEmpty();
            assertThat(round.soledadPlayer()).isEmpty();
            assertThat(round.soledadDeadline()).contains(DEADLINE);
        }

        @Test
        void shouldRecordDeclaringPlayerAsSoledadPlayer() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer());

            // Assert
            assertThat(round.soledadPlayer()).contains(declarer());
        }

        @Test
        void shouldCloseWindowImmediatelyWhenSomeoneDeclares() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer());

            // Assert
            assertThat(round.isWaitingForTrump()).isTrue();
            assertThat(round.soledadDeadline()).as("deadline no longer applies").isEmpty();
        }

        @Test
        void shouldKeepWindowOpenUntilAllFivePlayersPass() {
            // Arrange
            var round = newRound();

            // Act & Assert
            for (var seat = 0; seat < 4; seat++) {
                round = round.withSoledadPass(players.get(seat));
                assertThat(round.isWaitingForSoledad()).as("after %d pass(es)", seat + 1).isTrue();
            }
            round = round.withSoledadPass(players.get(4));
            assertThat(round.isWaitingForTrump()).isTrue();
        }

        @Test
        void shouldLeaveNoSoledadPlayerWhenEveryonePasses() {
            // Act
            final var round = passAll(newRound());

            // Assert
            assertThat(round.soledadPlayer()).isEmpty();
            assertThat(round.soledadPasses()).hasSize(5);
            assertThat(round.soledadDeadline()).isEmpty();
        }

        @Test
        void shouldRejectSecondPassFromSamePlayer() {
            // Arrange
            final var round = newRound().withSoledadPass(starter());

            // Act & Assert
            assertThatThrownBy(() -> round.withSoledadPass(starter()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already passed");
        }

        @Test
        void shouldRejectDeclarationFromPlayerOutsideTheRound() {
            // Arrange
            final var round = newRound();
            final var outsider = PlayerId.generate();

            // Act & Assert
            assertThatThrownBy(() -> round.withSoledadDeclared(outsider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in round");
        }

        @Test
        void shouldRejectDeclarationOnceTrumpIsSelected() {
            // Arrange
            final var round = passAll(newRound()).withTrump(TRUMP);

            // Act & Assert
            assertThatThrownBy(() -> round.withSoledadDeclared(declarer()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not waiting for soledad");
        }

        @Test
        void shouldRejectPassOnceSomeoneDeclared() {
            // Arrange
            final var round = newRound().withSoledadDeclared(declarer());

            // Act & Assert
            assertThatThrownBy(() -> round.withSoledadPass(starter()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not waiting for soledad");
        }
    }

    @Nested
    @DisplayName("Turn order (spec 2.5)")
    class TurnOrder {

        @Test
        @Disabled("GAP: withSoledadDeclared overwrites playerWhoGoes with the declarer. "
            + "Spec 2.5: the declarer chooses trump but the round still starts with the "
            + "player whose turn it is by normal rotation.")
        void shouldKeepNormalRotationStarterWhenSoledadIsDeclared() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer());

            // Assert
            assertThat(round.playerWhoGoes()).isEqualTo(starter());
            assertThat(round.soledadPlayer()).contains(declarer());
        }

        @Test
        @Disabled("GAP: the first basa is started by the declarer instead of the normal "
            + "rotation starter. See spec 2.5.")
        void shouldStartFirstBasaWithNormalRotationStarter() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);

            // Assert
            assertThat(round.currentBasa().orElseThrow().startingPlayer()).isEqualTo(starter());
            assertThat(round.currentPlayer()).contains(starter());
        }
    }

    @Nested
    @DisplayName("Teams (spec 2.5: one against four)")
    class NoTeams {

        @Test
        @Disabled("GAP: a Soledad round still forms a 2-vs-3 partnership on the first King, "
            + "giving the Soledad player a partner. A Soledad round must stay 1-vs-4.")
        void shouldRejectTeamFormationInSoledadRound() {
            // Arrange
            final var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);
            final var teams = Teams.of(round.playerWhoGoes(), players.get(1), new HashSet<>(players));

            // Act & Assert
            assertThatThrownBy(() -> round.withTeams(teams))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Round result (spec 2.5)")
    class RoundOutcome {

        @Test
        @Disabled("GAP: Round.checkForRoundEnd only understands 2-vs-3 teams, so a Soledad "
            + "round never ends on the Soledad player's fifth basa.")
        void shouldEndRoundWhenSoledadPlayerWinsFiveBasas() {
            // Arrange: the declarer takes every basa
            var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);

            // Act
            round = playBasasWonBy(round, List.of(
                declarer(), declarer(), declarer(), declarer(), declarer()
            ));

            // Assert
            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).contains(new RoundResult.Win(Set.of(declarer())));
            assertThat(round.completedBasas())
                .as("round stops as soon as the fifth basa is won")
                .hasSize(5);
        }

        @Test
        @Disabled("GAP: with fewer than five basas the other four players must win the round; "
            + "today the round is scored as an ordinary 2-vs-3 game or a draw.")
        void shouldEndRoundInFavourOfTheOtherFourWhenSoledadPlayerFallsShort() {
            // Arrange: the declarer takes nothing
            var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);
            final var opponents = players.stream()
                .filter(player -> !player.equals(declarer()))
                .toList();

            // Act
            round = playBasasWonBy(round, List.of(
                opponents.get(0), opponents.get(1), opponents.get(2), opponents.get(3),
                opponents.get(0), opponents.get(1), opponents.get(2), opponents.get(3)
            ));

            // Assert
            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).contains(new RoundResult.Win(Set.copyOf(opponents)));
        }
    }

    @Nested
    @DisplayName("Coins (spec 2.6)")
    class Scoring {

        private static final int SOLEDAD_STAKE_PER_OPPONENT = 3;
        private static final int SOLEDAD_TOTAL = SOLEDAD_STAKE_PER_OPPONENT * 4;

        @Test
        @Disabled("GAP: Game.calculateCoinChanges has no Soledad branch - it pays the flat "
            + "+/-2 team rate. Spec 2.6: the Soledad player wins 3 coins from each of the "
            + "other four (12 in total).")
        void shouldPaySoledadWinnerThreeCoinsFromEachOpponent() {
            // Arrange
            final var game = newGame();
            final var round = completedSoledadRoundWonBy(declarer());

            // Act
            final var changes = game.calculateCoinChanges(round, handsOf(round));

            // Assert
            assertThat(changes.get(declarer())).isEqualTo(SOLEDAD_TOTAL);
            for (final var opponent : opponents()) {
                assertThat(changes.get(opponent))
                    .as("opponent %s pays the Soledad stake", opponent)
                    .isEqualTo(-SOLEDAD_STAKE_PER_OPPONENT);
            }
            assertThat(changes.values().stream().mapToInt(Integer::intValue).sum())
                .as("coins are only moved between players, never created")
                .isZero();
        }

        @Test
        @Disabled("GAP: see shouldPaySoledadWinnerThreeCoinsFromEachOpponent. A losing "
            + "Soledad player pays 3 coins to each of the other four.")
        void shouldChargeSoledadLoserThreeCoinsForEachOpponent() {
            // Arrange
            final var game = newGame();
            final var round = completedSoledadRoundWonBy(opponents());

            // Act
            final var changes = game.calculateCoinChanges(round, handsOf(round));

            // Assert
            assertThat(changes.get(declarer())).isEqualTo(-SOLEDAD_TOTAL);
            for (final var opponent : opponents()) {
                assertThat(changes.get(opponent)).isEqualTo(SOLEDAD_STAKE_PER_OPPONENT);
            }
            assertThat(changes.values().stream().mapToInt(Integer::intValue).sum()).isZero();
        }
    }

    // === Helpers ===

    private Round newRound() {
        return Round.start(1, starter(), players, TestDeal.forPlayers(players).hands(), DEADLINE);
    }

    private Game newGame() {
        return Game.start(GameId.generate(), players, new Random(42), NOW);
    }

    private List<PlayerId> opponents() {
        return players.stream().filter(player -> !player.equals(declarer())).toList();
    }

    private Round passAll(Round round) {
        for (final var player : players) {
            round = round.withSoledadPass(player);
        }
        return round;
    }

    /**
     * Plays the five cards of the current basa, choosing a legal card for every player.
     */
    private Round playBasa(Round round) {
        for (var seat = 0; seat < 5; seat++) {
            final var current = round.currentPlayer().orElseThrow();
            round = round.withCardPlayed(current, LegalMoves.forRound(round, current), NOW);
        }
        return round;
    }

    /**
     * Plays basas, awarding each to the given winner in turn, and stops as soon as the
     * round decides it is over.
     */
    private Round playBasasWonBy(Round round, List<PlayerId> winners) {
        for (final var winner : winners) {
            if (round.isComplete()) {
                return round;
            }
            round = playBasa(round).completeBasa(winner);
        }
        return round;
    }

    private Round completedSoledadRoundWonBy(PlayerId winner) {
        return completedSoledadRoundWonBy(List.of(winner, winner, winner, winner, winner));
    }

    private Round completedSoledadRoundWonBy(List<PlayerId> basaWinners) {
        final var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);
        final var winners = new ArrayList<>(basaWinners);
        while (winners.size() < Round.MAX_BASAS) {
            winners.addAll(basaWinners);
        }
        return playBasasWonBy(round, winners.subList(0, Round.MAX_BASAS));
    }

    /**
     * The hands as they were dealt, which is what coin bonuses are calculated from.
     */
    private Map<PlayerId, List<Card>> handsOf(Round round) {
        return TestDeal.forPlayers(round.playerOrder()).hands();
    }
}
