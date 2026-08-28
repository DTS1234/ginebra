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
        void aCalledTodoThatWasMissedShouldCostTheGoingSideOne() {
            // Confirmed 2026-08-26: "you lose 1 if you don't make it, you earn one if you make".
            final var round = helped().goingSideCallsTodoAndMissesIt().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(round.todoCalled()).isTrue();
            assertThat(round.madeTodo()).isFalse();
            // 2 base + 1 primeres - 1 for the missed todo.
            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void primeresShouldBeWorthNothingToTheOpposingSide() {
            // "The other team does not get anything if they get primeres."
            final var round = helped().opposingSideTakesPrimeresAndBlocks().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(round.madePrimeres()).isFalse();
            for (final var player : round.opposingSide()) {
                assertThat(deltas.get(player))
                    .isEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD);
            }
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
        void estutxeShouldPayEveryPlayerOnTheGoingSide() {
            // Confirmed 2026-08-26: "you only get the money for the estuche if you go, and
            // both team players get the money". So the source's "Per guanyar i tindre
            // l'estutxe, 3 cadegú (si n té el dengue, 4)" comes out exactly.
            final var deal = dealWithEstutxe(ma());
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .as("the holder also has the dengue, which is personal")
                .isEqualTo(SettlementCalculator.BASE_HELPED + 2 * SettlementCalculator.INCREMENT);
            assertThat(deltas.get(aider()))
                .as("their partner collects the estutxe but not the dengue")
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
        }

        @Test
        void estutxeShouldRaiseWhatTheGoingSidePaysWhenItLoses() {
            // The 2026-08-27 correction: "pagan 1 más si no llegan a hacer 5". The
            // estutxe is a stake, not a fee for going - which is what makes the book's
            // "Si perden i tenen l'estutxe, 3 cadegú" come out at 3.
            final var deal = dealWithEstutxe(ma());
            final var round = helped().opposingSideBlocks().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(aider()))
                .as("-2 for the loss and -1 more for the estutxe, which is the side's")
                .isEqualTo(-SettlementCalculator.BASE_HELPED - SettlementCalculator.INCREMENT);
            assertThat(deltas.get(ma()))
                .as("the same, less the dengue they hold, which is collected either way")
                .isEqualTo(-SettlementCalculator.BASE_HELPED
                    - SettlementCalculator.INCREMENT
                    + SettlementCalculator.INCREMENT);
        }

        @Test
        void estutxeShouldCountWhenItIsMadeUpBetweenPartners() {
            // Confirmed 2026-08-26: the estutxe is shared, so it can be a connection of
            // cards across both players. The ma holds the dengue, the aider the manilla.
            final var deal = TestDeal.forPlayers(players)
                .give(ma(), Card.espadilla(), Card.basto())
                .give(aider(), MANILLA)
                .hands();
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .as("the dengue is personal, the estutxe is the side's: 2 + 1 + 1")
                .isEqualTo(SettlementCalculator.BASE_HELPED + 2 * SettlementCalculator.INCREMENT);
            assertThat(deltas.get(aider()))
                .as("their partner collects the estutxe only: 2 + 1")
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
        }

        @Test
        void estutxeShouldCountEvenWhenNobodyHoldsTheDengue() {
            // One partner takes the espadilla and manilla, the other the basto. The side
            // has the estutxe; nobody has the dengue. This is exactly the case the source's
            // "(si n té el dengue, 4)" is a parenthetical for.
            final var deal = TestDeal.forPlayers(players)
                .give(ma(), Card.espadilla(), MANILLA)
                .give(aider(), Card.basto())
                .hands();
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
            assertThat(deltas.get(aider()))
                .as("both collect the estutxe, neither collects a dengue")
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
        }

        @Test
        void estutxeShouldNotBeMadeUpAcrossTheTwoSides() {
            // The ma has the dengue, an opponent has the manilla. No estutxe for anyone.
            final var deal = TestDeal.forPlayers(players)
                .give(ma(), Card.espadilla(), Card.basto())
                .give(opponent(), MANILLA)
                .hands();
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma()))
                .as("the dengue only")
                .isEqualTo(SettlementCalculator.BASE_HELPED + SettlementCalculator.INCREMENT);
            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void dengueShouldNeverBeMadeUpBetweenPartners() {
            // The ma has the espadilla, the aider the basto. Neither holds a dengue.
            final var deal = TestDeal.forPlayers(players)
                .give(ma(), Card.espadilla())
                .give(aider(), Card.basto())
                .give(players.get(4), MANILLA)
                .hands();
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void estutxeShouldPayNothingToTheOpposingSide() {
            final var deal = dealWithEstutxe(opponent());
            final var round = helped().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, deal).playerDeltas();

            assertThat(deltas.get(opponent()))
                .as("only the dengue, which anyone collects")
                .isEqualTo(SettlementCalculator.INCREMENT);
            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_HELPED);
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
        void opponentsShouldCollectOneWhateverTheGoingSideManaged() {
            // "Si los 3 que no van hacen 5 cobran 1." A flat coin each: the book's extra
            // for holding them under four is not played - payment-rules.md §6.
            final var swept = helped().opposingSideBlocks().round();
            final var close = helped().fourAllThenBlocked().round();

            for (final var round : List.of(swept, close)) {
                final var deltas = calculator.settle(round, plainDeal()).playerDeltas();
                for (final var player : List.of(players.get(2), players.get(3), players.get(4))) {
                    assertThat(deltas.get(player))
                        .as("going side made %d", round.goingSideBasas())
                        .isEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD);
                }
            }
        }

        @Test
        void aFourAllFinishShouldStillCostTheGoingSideItsStake() {
            // Nobody reached five. "Sino hacen más de 4 pagan 2."
            final var round = helped().fourAllThenBlocked().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(round.goingSideBasas()).isEqualTo(4);
            assertThat(deltas.get(ma())).isEqualTo(-SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(aider())).isEqualTo(-SettlementCalculator.BASE_HELPED);
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
    @DisplayName("The king dragged out of you")
    class ForcedKing {

        @Test
        void shouldCostAForcedHelperNothing() {
            // "A king forced out of anyone else costs nothing" - the players, 2026-08-27.
            // They are the helper now, whether they meant to be or not.
            final var round = helpedByAForcedKing().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(aider())).isEqualTo(SettlementCalculator.BASE_HELPED);
            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_HELPED);
        }

        @Test
        void shouldChargeTheMaAloneWhenTheyStop() {
            // "En la segunda, solo pagas 1." Nobody else pays, and nobody collects - the
            // hand simply did not happen.
            final var round = inProgress()
                .withKingPlayed(ma(), true)
                .withKingChoice(ma(), false);

            final var settlement = calculator.settle(round, plainDeal());
            final var deltas = settlement.playerDeltas();

            assertThat(round.isComplete()).isTrue();
            assertThat(deltas.get(ma())).isEqualTo(-SettlementCalculator.STOPPING_COST);
            for (final var player : players.subList(1, 5)) {
                assertThat(deltas.get(player)).isZero();
            }
            assertThat(settlement.totalCollected())
                .as("the pot pays nothing out on a fallen king")
                .isZero();
        }

        @Test
        void shouldPayNothingElseOnAHandThatDidNotHappen() {
            // Not even a dengue: "a los demás no se les cobra nada", and nobody collects
            // either - the cards are simply dealt again.
            final var round = inProgress()
                .withKingPlayed(ma(), true)
                .withKingChoice(ma(), false);

            final var deltas = calculator.settle(round, dealWithDengue(opponent())).playerDeltas();

            assertThat(deltas.get(opponent())).isZero();
            assertThat(deltas.get(ma())).isEqualTo(-SettlementCalculator.STOPPING_COST);
        }

        @Test
        void shouldStakeFourWhenTheyCarryOn() {
            // "En la primera opción, cobras 4 si haces 5 o pagas 4 si no haces más que 4."
            final var round = maCarriesOnAfterTheirKingFell().goingSideWinsFive().round();

            final var deltas = calculator.settle(round, plainDeal()).playerDeltas();

            assertThat(deltas.get(ma())).isEqualTo(SettlementCalculator.BASE_SELF_KING);
        }
    }

    @Nested
    @DisplayName("The pot absorbs the difference")
    class PotBalance {

        @Test
        void shouldTakeInMoreThanItPaysOutWhenTheGoingSideFails() {
            final var settlement = calculator.settle(
                helped().opposingSideBlocks().round(), plainDeal()
            );

            // 4 in from the pair, 3 out to the three opponents: the pot keeps the coin.
            assertThat(settlement.possoDelta()).isEqualTo(4 - 3);
            assertThat(settlement.totalCollected()).isEqualTo(3);
        }

        @Test
        void shouldFundTheWinEntirelyFromThePot() {
            final var settlement = calculator.settle(
                helped().goingSideWinsFive().round(), plainDeal()
            );

            assertThat(settlement.possoDelta()).isEqualTo(-2 * SettlementCalculator.BASE_HELPED);
        }
    }

    /**
     * The book prints two tables of figures. This prices every row of both that the
     * engine can reach, and they all come out as written - which is the evidence that the
     * 2026-08-27 correction is right, since two of them did not before it.
     *
     * @see <a href="file:payment-rules.md">payment-rules.md §4</a>
     */
    @Nested
    @DisplayName("Every row of the book, priced")
    class TheBookTables {

        // --- §5, what you collect ---------------------------------------------------

        @Test
        void siEtPosenElReiIGuanyes_2() {
            assertGoingSideGets(helped().goingSideWinsFive(), plainDeal(), 2, 2);
        }

        @Test
        void perGuanyarIFerPrimeres_3() {
            assertGoingSideGets(helped().goingSideWinsFiveWithPrimeres(), plainDeal(), 3, 3);
        }

        @Test
        void perGuanyarITindreLEstutxe_3_or_4WithTheDengue() {
            assertGoingSideGets(helped().goingSideWinsFive(), dealWithEstutxe(ma()), 4, 3);
        }

        @Test
        void perGuanyarEstutxeIPrimeres_4_or_5WithTheDengue() {
            assertGoingSideGets(
                helped().goingSideWinsFiveWithPrimeres(), dealWithEstutxe(ma()), 5, 4
            );
        }

        @Test
        void siEtPosesElReiIGuanyes_4() {
            assertGoingSideGets(selfKing().goingSideWinsFive(), plainDeal(), 4, null);
        }

        @Test
        void siEtPosesElReiIGuanyesIPrimeres_5() {
            assertGoingSideGets(selfKing().goingSideWinsFiveWithPrimeres(), plainDeal(), 5, null);
        }

        @Test
        void siVasASolesIGuanyes_5() {
            final var deltas = settle(soledad().goingSideWinsFive(), plainDeal());
            assertThat(deltas.get(ma())).isEqualTo(5);
        }

        @Test
        void siVasASolesGuanyesIPrimeres_6() {
            final var deltas = settle(soledad().goingSideWinsFiveWithPrimeres(), plainDeal());
            assertThat(deltas.get(ma())).isEqualTo(6);
        }

        @Test
        void siTensElDengue_1() {
            final var deltas = settle(helped().goingSideWinsFive(), dealWithDengue(opponent()));
            assertThat(deltas.get(opponent()))
                .as("collected by an opponent of the winning side, and still collected")
                .isEqualTo(1);
        }

        // --- §6, what you pay -------------------------------------------------------

        @Test
        void siTAidenIPerds_2() {
            assertGoingSideGets(helped().opposingSideBlocks(), plainDeal(), -2, -2);
        }

        @Test
        void siPerdenIPrimeres_3() {
            assertGoingSideGets(
                helped().goingSideMakesPrimeresThenLosesFourAll(), plainDeal(), -3, -3
            );
        }

        @Test
        void siPerdenITenenLEstutxe_3() {
            // The row that did not come out before the correction: the estutxe used to be
            // collected for going, which left this at 1.
            assertGoingSideGets(helped().opposingSideBlocks(), dealWithEstutxe(ma()), -2, -3);
        }

        @Test
        void siPerdenPrimeresIEstutxe_4() {
            // The other one.
            assertGoingSideGets(
                helped().goingSideMakesPrimeresThenLosesFourAll(), dealWithEstutxe(ma()), -3, -4
            );
        }

        @Test
        void siEtPosesElReiPerds_4() {
            assertGoingSideGets(selfKing().opposingSideBlocks(), plainDeal(), -4, null);
        }

        @Test
        void siVasASolesIPerds_5() {
            final var deltas = settle(soledad().opposingSideBlocks(), plainDeal());
            assertThat(deltas.get(ma())).isEqualTo(-5);
        }

        @Test
        void siEtCauElRei_1() {
            final var round = inProgress().withKingPlayed(ma(), true).withKingChoice(ma(), false);

            assertThat(calculator.settle(round, plainDeal()).playerDeltas().get(ma()))
                .isEqualTo(-1);
        }

        private Map<PlayerId, Integer> settle(Scenario scenario, Map<PlayerId, List<Card>> deal) {
            return calculator.settle(scenario.round(), deal).playerDeltas();
        }

        /**
         * @param forMa what the ma nets - which includes their dengue, where the deal
         *              gives them one, since the book prints that as a variant
         * @param forAider what the other of the pair nets, or null when there is no pair
         */
        private void assertGoingSideGets(
            Scenario scenario,
            Map<PlayerId, List<Card>> deal,
            int forMa,
            Integer forAider
        ) {
            final var deltas = settle(scenario, deal);
            assertThat(deltas.get(ma())).as("the one who goes").isEqualTo(forMa);
            if (forAider != null) {
                assertThat(deltas.get(aider())).as("the one who put the king").isEqualTo(forAider);
            }
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

    /** The ma's own king was dragged out of them and they chose to play it out alone. */
    private Scenario maCarriesOnAfterTheirKingFell() {
        return new Scenario(
            inProgress().withKingPlayed(ma(), true).withKingChoice(ma(), true)
        );
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

        /** Five in a row, todo called, then one dropped. */
        private Scenario goingSideCallsTodoAndMissesIt() {
            for (var i = 0; i < 5; i++) {
                award(goingPlayer());
            }
            award(opposingPlayer());
            return this;
        }

        /** The opponents take the first five, the first four of them their primeres. */
        private Scenario opposingSideTakesPrimeresAndBlocks() {
            for (var i = 0; i < Round.BASAS_TO_WIN; i++) {
                award(opposingPlayer());
            }
            return this;
        }

        private Scenario opposingSideBlocks() {
            for (var i = 0; i < Round.BASAS_TO_WIN; i++) {
                award(opposingPlayer());
            }
            return this;
        }

        /** Four each: the going side never reaches five, so it fails on the last basa. */
        /**
         * The going side takes the first four - their primeres - and the opponents take
         * the last four. Nobody reaches five, and the going side pays.
         *
         * It is the only way primeres and a loss can happen together: four for the going
         * side leaves only four for the opponents, so they can never reach five.
         */
        private Scenario goingSideMakesPrimeresThenLosesFourAll() {
            for (var i = 0; i < 4; i++) {
                award(goingPlayer());
            }
            for (var i = 0; i < 4; i++) {
                award(opposingPlayer());
            }
            return this;
        }

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

            // A clean sweep to five pauses on the todo call; these scenarios play on.
            if (round.isWaitingForTodo()) {
                round = round.withTodoCalled(round.todoCaller().orElseThrow());
            }
        }
    }
}
