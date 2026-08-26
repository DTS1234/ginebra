package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.support.TestDeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The settlement ladder of rules-source.md §5 and §6, priced row by row.
 *
 * Deals are built so nobody accidentally holds the dengue, which would add a coin to every
 * figure; the tests that want it pin it deliberately.
 */
@DisplayName("Settlement against the posso")
class SettlementCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(120);
    private static final Suit TRUMP = Suit.COPAS;
    private static final Card MANILLA = new Card(Suit.COPAS, Rank.SIETE);

    private final SettlementCalculator calculator = new SettlementCalculator();

    private final List<PlayerId> players = List.of(
        PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
        PlayerId.generate(), PlayerId.generate()
    );

    /** The mà, and the going side in every mode below. */
    private PlayerId ma() {
        return players.get(0);
    }

    private PlayerId aider() {
        return players.get(1);
    }

    private PlayerId opponent() {
        return players.get(2);
    }

    @Nested
    @DisplayName("Base rates")
    class BaseRates {

        @Test
        void helpedPairShouldCollectTwoEach() {
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(players.get(2))).isZero();
            assertThat(deltas.get(players.get(3))).isZero();
            assertThat(deltas.get(players.get(4))).isZero();
        }

        @Test
        void puttingYourOwnKingShouldCollectFour() {
            final var round = selfKing().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_SELF_KING);
        }

        @Test
        void goingAloneShouldCollectFive() {
            final var round = soledad().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_SOLEDAD);
        }

        @Test
        void fourKingsShouldCollectFourWhenTheHolderDeclinesToPlay() {
            final var hands = fourKingDeal();
            final var round = Round.start(1, ma(), players, hands, DEADLINE)
                .withSoledadPass(opponent());

            final var deltas = calculator.settle(round, hands).playerDeltas();

            assertThat(round.isComplete()).as("declining ends the hand").isTrue();
            assertThat(deltas.get(opponent())).isEqualTo(SettlementCalculator.FOUR_KINGS_AWARD);
            assertThat(deltas.get(ma())).isZero();
        }

        @Test
        void fourKingsShouldStackOnTheSoledadRateWhenTheHolderPlaysItOut() {
            final var hands = fourKingDeal();
            var round = Round.start(1, ma(), players, hands, DEADLINE)
                .withSoledadDeclared(opponent())
                .withTrump(TRUMP);
            final var scenario = new Scenario(round);

            final var deltas = calculator
                .settle(scenario.goingSideWinsFive().round(), hands).playerDeltas();

            // "5 d'anar a soles, 4 des 4 reis" - the source's own arithmetic.
            assertThat(deltas.get(opponent())).isEqualTo(
                SettlementCalculator.BASE_SOLEDAD + SettlementCalculator.FOUR_KINGS_AWARD
            );
        }

        @Test
        void fourKingsShouldStillBeCollectedWhenTheHolderPlaysOnAndLoses() {
            final var hands = fourKingDeal();
            var round = Round.start(1, ma(), players, hands, DEADLINE)
                .withSoledadDeclared(opponent())
                .withTrump(TRUMP);
            final var scenario = new Scenario(round);

            final var deltas = calculator
                .settle(scenario.opposingSideBlocks().round(), hands).playerDeltas();

            // The 4 is a holding award like the dengue, so it survives the loss: -5 + 4.
            assertThat(deltas.get(opponent())).isEqualTo(
                SettlementCalculator.FOUR_KINGS_AWARD - SettlementCalculator.BASE_SOLEDAD
            );
        }
    }

    @Nested
    @DisplayName("Increments, each worth one coin")
    class Increments {

        @Test
        void primeresShouldAddOne() {
            final var round = helped().goingSideWinsFiveWithPrimeres().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(round.madePrimeres()).isTrue();
            assertThat(deltas.get(ma()))
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
        }

        @Test
        void todoShouldAddOneOnTopOfPrimeres() {
            final var round = helped().goingSideWinsEverything().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(round.madeTodo()).isTrue();
            assertThat(round.madePrimeres()).as("todo contains primeres").isTrue();
            assertThat(deltas.get(ma()))
                .isEqualTo(SettlementCalculator.BASE_HELPED + 2 * SettlementCalculator.INCREMENT);
        }

        @Test
        void dengueShouldAddOneToItsHolder() {
            final var deal = dealWithDengue(ma());
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void estutxeShouldAddOneOnTopOfTheDengue() {
            final var deal = dealWithEstutxe(ma());
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            // "Si tens l'estutxe i vas, 2" - the estutxe contains the dengue, so both score.
            assertThat(deltas.get(ma()))
                .isEqualTo(SettlementCalculator.BASE_HELPED + 2 * SettlementCalculator.INCREMENT);
        }

        @Test
        void theSourcesMaximumOfThirteenShouldAddUp() {
            // "5 d'anar a soles, 4 des 4 reis, 1 de primeres, 2 de l'estutxe i 1 de todo"
            final var maximum = SettlementCalculator.BASE_SOLEDAD
                + SettlementCalculator.FOUR_KINGS_AWARD
                + SettlementCalculator.INCREMENT               // primeres
                + 2 * SettlementCalculator.INCREMENT           // dengue + estutxe
                + SettlementCalculator.INCREMENT;              // todo

            assertThat(maximum).isEqualTo(13);
        }
    }

    @Nested
    @DisplayName("When the going side fails")
    class Failure {

        @Test
        void goingSideShouldPayTheSameFigureItWouldHaveCollected() {
            final var round = helped().opposingSideBlocks().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(-SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(aider())).isEqualTo(-SettlementCalculator.BASE_HELPED);
        }

        @Test
        void opponentsShouldCollectTwoWhenTheGoingSideIsHeldUnderFour() {
            final var round = helped().opposingSideBlocks().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            for (final var player : List.of(players.get(2), players.get(3), players.get(4))) {
                assertThat(deltas.get(player))
                    .isEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD_HELD_LOW);
            }
        }

        @Test
        void opponentsShouldCollectOneWhenTheGoingSideGotFourBasas() {
            final var round = helped().fourAllThenBlocked().round();

            final var settlement = calculator.settle(round, plainDeal());

            assertThat(round.goingSideBasas()).isEqualTo(4);
            for (final var player : List.of(players.get(2), players.get(3), players.get(4))) {
                assertThat(settlement.playerDeltas().get(player))
                    .isEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD);
            }
        }

        @Test
        void aLosingSideShouldStillCollectItsDengue() {
            // "El dengue sempre es cobra."
            final var deal = dealWithDengue(ma());
            final var round = helped().opposingSideBlocks().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .isEqualTo(-SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
        }

        @Test
        void anOpponentShouldCollectTheirDengueToo() {
            final var deal = dealWithDengue(opponent());
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(opponent()))
                .as("the dengue is not conditional on winning, or on which side you are")
                .isEqualTo(SettlementCalculator.INCREMENT);
        }
    }

    @Nested
    @DisplayName("The forced king")
    class ForcedKing {

        @Test
        void shouldChargeOneToWhoeverHadTheirKingDraggedOut() {
            final var round = helpedByAForcedKing().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(aider()))
                .isEqualTo(SettlementCalculator.BASE_HELPED - SettlementCalculator.FORCED_KING_PENALTY);
            assertThat(deltas.get(ma()))
                .as("the penalty is personal to the king's owner")
                .isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void shouldChargeTheMaAloneWhenTheirOwnKingEndsTheHand() {
            // Confirmed by the players 2026-08-26: "A pays 1 and we redeal, no other
            // players charged." Nobody collects either - the hand simply did not happen.
            final var round = inProgress().withKingPlayed(ma(), true);

            final var settlement = calculator.settle(round, plainDeal());
            final var deltas = settlement.playerDeltas();

            assertThat(round.isComplete()).isTrue();
            assertThat(deltas.get(ma())).isEqualTo(-SettlementCalculator.FORCED_KING_PENALTY);
            for (final var player : players.subList(1, 5)) {
                assertThat(deltas.get(player)).isZero();
            }
            assertThat(settlement.totalCollected())
                .as("the pot pays nothing out on a fallen king")
                .isZero();
        }
    }

    @Nested
    @DisplayName("The pot absorbs the difference")
    class PotBalance {

        @Test
        void shouldPayOutMoreThanItTakesWhenTheGoingSideFails() {
            final var settlement = calculator.settle(
                helped().opposingSideBlocks().round(), plainDeal()
            );

            // 4 in from the pair, 6 out to the three opponents.
            assertThat(settlement.possoDelta()).isEqualTo(4 - 6);
            assertThat(settlement.totalCollected()).isEqualTo(6);
        }

        @Test
        void shouldFundTheWinEntirelyFromThePot() {
            final var settlement = calculator.settle(
                helped().goingSideWinsFive().round(), plainDeal()
            );

            assertThat(settlement.possoDelta()).isEqualTo(-2 * SettlementCalculator.BASE_HELPED);
        }
    }

    @Test
    void shouldRefuseToSettleARoundStillInPlay() {
        assertThatThrownBy(() -> calculator.settle(inProgress(), plainDeal()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not complete");
    }

    // === Deals ===

    /** Every special card in a different hand, so nobody holds the dengue. */
    private Map<PlayerId, List<Card>> plainDeal() {
        return TestDeal.forPlayers(players)
            .give(players.get(0), Card.espadilla())
            .give(players.get(1), Card.basto())
            .give(players.get(3), MANILLA)
            .hands();
    }

    private Map<PlayerId, List<Card>> dealWithDengue(PlayerId holder) {
        return TestDeal.forPlayers(players)
            .give(holder, Card.espadilla(), Card.basto())
            .give(players.get(4), MANILLA)
            .hands();
    }

    private Map<PlayerId, List<Card>> dealWithEstutxe(PlayerId holder) {
        return TestDeal.forPlayers(players)
            .give(holder, Card.espadilla(), Card.basto(), MANILLA)
            .hands();
    }

    /** All four kings in one hand, which ends the round at the deal. */
    private Map<PlayerId, List<Card>> fourKingDeal() {
        return TestDeal.forPlayers(players)
            .give(opponent(),
                new Card(Suit.COPAS, Rank.REY),
                new Card(Suit.OROS, Rank.REY),
                new Card(Suit.ESPADAS, Rank.REY),
                new Card(Suit.BASTOS, Rank.REY))
            .hands();
    }

    // === Round scenarios ===

    private Round inProgress() {
        var round = Round.start(1, ma(), players, plainDeal(), DEADLINE);
        for (final var player : players) {
            round = round.withSoledadPass(player);
        }
        return round.withTrump(TRUMP);
    }

    private Scenario helped() {
        return new Scenario(inProgress().withKingPlayed(aider(), false));
    }

    private Scenario helpedByAForcedKing() {
        return new Scenario(inProgress().withKingPlayed(aider(), true));
    }

    private Scenario selfKing() {
        return new Scenario(inProgress().withKingPlayed(ma(), false));
    }

    private Scenario soledad() {
        var round = Round.start(1, ma(), players, plainDeal(), DEADLINE)
            .withSoledadDeclared(ma())
            .withTrump(TRUMP);
        return new Scenario(round);
    }

    /**
     * Drives a round to an outcome by awarding basas, without caring which cards are played.
     */
    private final class Scenario {
        private Round round;

        private Scenario(Round round) {
            this.round = round;
        }

        private Round round() {
            return round;
        }

        /** An opponent takes the first, killing "todo", then the going side takes five. */
        private Scenario goingSideWinsFive() {
            award(opposingPlayer());
            for (var i = 0; i < 5; i++) {
                award(goingPlayer());
            }
            return this;
        }

        /** The going side takes the first four, concedes one, then takes its fifth. */
        private Scenario goingSideWinsFiveWithPrimeres() {
            for (var i = 0; i < 4; i++) {
                award(goingPlayer());
            }
            award(opposingPlayer());
            award(goingPlayer());
            return this;
        }

        private Scenario goingSideWinsEverything() {
            for (var i = 0; i < Round.MAX_BASAS; i++) {
                award(goingPlayer());
            }
            return this;
        }

        private Scenario opposingSideBlocks() {
            for (var i = 0; i < Round.BASAS_TO_BLOCK; i++) {
                award(opposingPlayer());
            }
            return this;
        }

        /** Four each: the going side never reaches five, so it fails on the last basa. */
        private Scenario fourAllThenBlocked() {
            for (var i = 0; i < 4; i++) {
                award(goingPlayer());
                award(opposingPlayer());
            }
            return this;
        }

        private PlayerId goingPlayer() {
            return round.goingSide().iterator().next();
        }

        /** Someone actually on the other side, whoever the going side turns out to be. */
        private PlayerId opposingPlayer() {
            return round.opposingSide().iterator().next();
        }

        private void award(PlayerId winner) {
            if (round.isComplete()) {
                return;
            }
            for (var seat = 0; seat < 5; seat++) {
                final var current = round.currentPlayer().orElseThrow();
                final var hand = new ArrayList<>(round.getHand(current));
                round = round.withCardPlayed(current, hand.get(0), NOW);
            }
            round = round.completeBasa(winner);
        }
    }
}
