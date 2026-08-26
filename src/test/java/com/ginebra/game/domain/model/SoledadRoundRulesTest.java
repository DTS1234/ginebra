package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.support.LegalMoves;
import com.ginebra.game.domain.service.SettlementCalculator;
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
 * The declarer names trumps but does not take over the lead: the mà still leads the first
 * basa, which is what reconciles rules-source.md §4.1 with the glossary's "Ser mà [...] És
 * es primer en jugar".
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
        void shouldKeepNormalRotationStarterWhenSoledadIsDeclared() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer());

            // Assert
            assertThat(round.playerWhoGoes()).isEqualTo(starter());
            assertThat(round.soledadPlayer()).contains(declarer());
        }

        @Test
        void shouldMakeTheDeclarerNameTrumpsWithoutMakingThemMa() {
            // Act
            final var round = newRound().withSoledadDeclared(declarer());

            // Assert
            assertThat(round.trumpChooser()).isEqualTo(declarer());
            assertThat(round.playerWhoGoes()).isEqualTo(starter());
        }

        @Test
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
    @DisplayName("Round result: five alone, or the other four take it")
    class RoundOutcome {

        @Test
        void shouldEndRoundWhenSoledadPlayerWinsFiveBasas() {
            // Arrange: an opponent takes the first, so "todo" is off the table
            var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);

            // Act
            round = playBasasWonBy(round, List.of(
                opponents().get(0),
                declarer(), declarer(), declarer(), declarer(), declarer()
            ));

            // Assert
            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).contains(new RoundResult.GoingSideWon(
                Set.of(declarer()), Set.copyOf(opponents()), 5
            ));
            assertThat(round.completedBasas())
                .as("round stops as soon as the fifth basa is won")
                .hasSize(6);
        }

        @Test
        void shouldPlayOnPastFiveWhileTodoIsStillLive() {
            // Arrange: the declarer takes every basa, so "fer todo" stays reachable
            var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);

            // Act
            round = playBasasWonBy(round, List.of(
                declarer(), declarer(), declarer(), declarer(), declarer()
            ));

            // Assert
            assertThat(round.isComplete()).as("five is not the end when todo is live").isFalse();
            assertThat(round.goingSideBasas()).isEqualTo(5);
        }

        @Test
        void shouldEndRoundInFavourOfTheOtherFourOnTheirFourthBasa() {
            // Arrange: the declarer takes nothing
            var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);

            // Act
            round = playBasasWonBy(round, List.of(
                opponents().get(0), opponents().get(1), opponents().get(2), opponents().get(3),
                opponents().get(0), opponents().get(1), opponents().get(2), opponents().get(3)
            ));

            // Assert
            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).contains(new RoundResult.GoingSideFailed(
                Set.of(declarer()), Set.copyOf(opponents()), 0
            ));
            assertThat(round.completedBasas())
                .as("four basas already put five out of reach")
                .hasSize(4);
        }
    }

    @Nested
    @DisplayName("Coins: anar a soles settles at 5 against the posso")
    class Scoring {

        private static final int SOLEDAD_STAKE = SettlementCalculator.BASE_SOLEDAD;

        private final SettlementCalculator calculator = new SettlementCalculator();

        @Test
        void shouldPaySoledadWinnerFiveFromThePosso() {
            // Arrange
            final var round = completedSoledadRoundWonByDeclarer();

            // Act
            final var settlement = calculator.settle(round, dealtHands());

            // Assert
            assertThat(settlement.playerDeltas().get(declarer())).isEqualTo(SOLEDAD_STAKE);
            for (final var opponent : opponents()) {
                assertThat(settlement.playerDeltas().get(opponent))
                    .as("a losing opponent neither collects nor pays")
                    .isZero();
            }
            assertThat(settlement.possoDelta())
                .as("the pot funds the win")
                .isEqualTo(-SOLEDAD_STAKE);
        }

        @Test
        void shouldChargeSoledadLoserFiveAndPayTheOthersOne() {
            // Arrange: the declarer is held to nothing
            final var round = completedSoledadRoundWonByOpponents();

            // Act
            final var settlement = calculator.settle(round, dealtHands());

            // Assert
            assertThat(settlement.playerDeltas().get(declarer())).isEqualTo(-SOLEDAD_STAKE);
            for (final var opponent : opponents()) {
                assertThat(settlement.playerDeltas().get(opponent))
                    .as("held under four basas, so each opponent collects 2")
                    .isEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD_HELD_LOW);
            }
        }

        @Test
        void shouldNotBalanceBecauseThePotAbsorbsTheDifference() {
            // Arrange
            final var round = completedSoledadRoundWonByOpponents();

            // Act
            final var settlement = calculator.settle(round, dealtHands());

            // Assert: 5 in from the declarer, 8 out to the four opponents
            assertThat(settlement.possoDelta())
                .as("the pot pays out more than it takes, which is what it is for")
                .isEqualTo(SOLEDAD_STAKE - 4 * SettlementCalculator.OPPOSING_SIDE_AWARD_HELD_LOW);
        }
    }

    // === Helpers ===

    private Round newRound() {
        return Round.start(1, starter(), players, deal(), DEADLINE);
    }

    /**
     * Espadilla, Basto and Manilla go to three different players, so nobody holds the
     * dengue and the settlement figures stay the base rates.
     */
    private Map<PlayerId, List<Card>> deal() {
        return TestDeal.forPlayers(players)
            .give(players.get(0), Card.espadilla())
            .give(players.get(1), Card.basto())
            .give(players.get(3), new Card(TRUMP, Rank.SIETE))
            .hands();
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

    /** An opponent takes the first basa, then the declarer takes five. */
    private Round completedSoledadRoundWonByDeclarer() {
        final var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);
        return playBasasWonBy(round, List.of(
            opponents().get(0),
            declarer(), declarer(), declarer(), declarer(), declarer()
        ));
    }

    /** The declarer is held to nothing at all. */
    private Round completedSoledadRoundWonByOpponents() {
        final var round = newRound().withSoledadDeclared(declarer()).withTrump(TRUMP);
        return playBasasWonBy(round, List.of(
            opponents().get(0), opponents().get(1), opponents().get(2), opponents().get(3)
        ));
    }

    /**
     * The hands as they were dealt, which is what the dengue and estutxe are read from.
     */
    private Map<PlayerId, List<Card>> dealtHands() {
        return deal();
    }
}
