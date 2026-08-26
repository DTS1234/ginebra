package com.ginebra.game.domain.model;

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
 * The king rules of rules-source.md §4.3, §4.7 and §4.8, which decide the shape of a round.
 *
 * A king either aids the one who goes, or - played by the one who goes - either puts them
 * alone against four or ends the hand outright, depending on whether they had a choice.
 */
@DisplayName("King rules")
class KingRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(120);
    private static final Suit TRUMP = Suit.COPAS;

    private final List<PlayerId> players = List.of(
        PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
        PlayerId.generate(), PlayerId.generate()
    );

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
    @DisplayName("Posar el rei: another player aids")
    class Helped {

        @Test
        void shouldPutTheAiderAndTheMaTogetherAgainstThree() {
            final var round = inProgress().withKingPlayed(aider(), false);

            assertThat(round.mode()).contains(RoundMode.HELPED);
            assertThat(round.goingSide()).containsExactlyInAnyOrder(ma(), aider());
            assertThat(round.opposingSide())
                .containsExactlyInAnyOrder(players.get(2), players.get(3), players.get(4));
        }

        @Test
        void shouldFormThePartnershipEvenWhenTheKingWasForced() {
            // "Moltes voltes poses el rei sense voler perquè et cau."
            final var round = inProgress().withKingPlayed(aider(), true);

            assertThat(round.mode()).contains(RoundMode.HELPED);
            assertThat(round.goingSide()).containsExactlyInAnyOrder(ma(), aider());
            assertThat(round.forcedKingPlayer()).contains(aider());
        }

        @Test
        void shouldRecordNoPenaltyWhenTheKingWasPlayedByChoice() {
            final var round = inProgress().withKingPlayed(aider(), false);

            assertThat(round.forcedKingPlayer()).isEmpty();
        }

        @Test
        void shouldRefuseASecondKingOnceTheSideIsDecided() {
            final var round = inProgress().withKingPlayed(aider(), false);

            assertThatThrownBy(() -> round.withKingPlayed(opponent(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");
        }
    }

    @Nested
    @DisplayName("Posar-se el rei: the ma puts their own king")
    class SelfKing {

        @Test
        void shouldLeaveTheMaAloneAgainstFour() {
            final var round = inProgress().withKingPlayed(ma(), false);

            assertThat(round.mode()).contains(RoundMode.SELF_KING);
            assertThat(round.goingSide()).containsExactly(ma());
            assertThat(round.opposingSide()).hasSize(4).doesNotContain(ma());
        }

        @Test
        void shouldStillNeedFiveBasasToWin() {
            var round = inProgress().withKingPlayed(ma(), false);
            round = award(round, opponent());
            for (var i = 0; i < 5; i++) {
                round = award(round, ma());
            }

            assertThat(round.result()).contains(new RoundResult.GoingSideWon(
                java.util.Set.of(ma()), round.opposingSide(), 5
            ));
        }

        @Test
        void shouldFailWhenTheOtherFourTakeFour() {
            var round = inProgress().withKingPlayed(ma(), false);
            for (var i = 0; i < Round.BASAS_TO_BLOCK; i++) {
                round = award(round, opponent());
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result().orElseThrow()).isInstanceOf(RoundResult.GoingSideFailed.class);
        }
    }

    @Nested
    @DisplayName("Caure el rei: the ma's king is forced out")
    class KingFell {

        @Test
        void shouldEndTheHandOnTheSpot() {
            // "Si es qui és mà li cau el rei s'acaba sa mà."
            final var round = inProgress().withKingPlayed(ma(), true);

            assertThat(round.isComplete()).isTrue();
            assertThat(round.mode()).contains(RoundMode.KING_FELL);
            assertThat(round.result()).contains(new RoundResult.KingFell(ma()));
            assertThat(round.currentBasa()).isEmpty();
        }

        @Test
        void shouldLeaveNobodyWinningAndChargeTheMa() {
            final var round = inProgress().withKingPlayed(ma(), true);

            assertThat(round.result().orElseThrow().winners()).isEmpty();
            assertThat(round.forcedKingPlayer()).contains(ma());
            assertThat(round.goingSide()).isEmpty();
        }
    }

    /**
     * rules-source.md §4.8: four kings in one hand is worth 4 and ends the hand - unless
     * the holder would rather play it out alone, which only they may do. Confirmed by the
     * players 2026-08-26: <i>"if you wanna try to win, then you go"</i>.
     */
    @Nested
    @DisplayName("Four kings in one hand")
    class FourKings {

        @Test
        void shouldRecordTheHolderWithoutEndingTheHandYet() {
            final var round = Round.start(1, ma(), players, fourKingDeal(opponent()), DEADLINE);

            assertThat(round.fourKingHolder()).contains(opponent());
            assertThat(round.isWaitingForSoledad())
                .as("the holder still has a choice to make")
                .isTrue();
            assertThat(round.mode()).isEmpty();
        }

        @Test
        void shouldEndTheHandWhenTheHolderDeclines() {
            final var round = Round.start(1, ma(), players, fourKingDeal(opponent()), DEADLINE)
                .withSoledadPass(opponent());

            assertThat(round.isComplete()).isTrue();
            assertThat(round.mode()).contains(RoundMode.FOUR_KINGS);
            assertThat(round.result()).contains(new RoundResult.FourKings(opponent()));
        }

        @Test
        void shouldTurnIntoASoledadWhenTheHolderPlaysItOut() {
            final var round = Round.start(1, ma(), players, fourKingDeal(opponent()), DEADLINE)
                .withSoledadDeclared(opponent());

            assertThat(round.mode()).contains(RoundMode.SOLEDAD);
            assertThat(round.goingSide()).containsExactly(opponent());
            assertThat(round.trumpChooser()).isEqualTo(opponent());
            assertThat(round.fourKingHolder())
                .as("they keep the four kings on top of whatever the hand settles at")
                .contains(opponent());
        }

        @Test
        void shouldRefuseToLetAnyoneElseGoAloneAgainstAFourKingDeal() {
            // "Si en es mateix temps un altre jugador vullguera anar a soles, no podria."
            final var round = Round.start(1, ma(), players, fourKingDeal(opponent()), DEADLINE);

            assertThatThrownBy(() -> round.withSoledadDeclared(ma()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("four kings");
        }

        @Test
        void shouldNotFireForThreeKings() {
            final var hands = TestDeal.forPlayers(players)
                .give(opponent(),
                    new Card(Suit.COPAS, Rank.REY),
                    new Card(Suit.OROS, Rank.REY),
                    new Card(Suit.ESPADAS, Rank.REY))
                .give(ma(), new Card(Suit.BASTOS, Rank.REY))
                .hands();

            final var round = Round.start(1, ma(), players, hands, DEADLINE);

            assertThat(round.fourKingHolder()).isEmpty();
            assertThat(round.isWaitingForSoledad()).isTrue();
            assertThat(round.mode()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Primeres and todo")
    class PrimeresAndTodo {

        @Test
        void shouldReportPrimeresForTheFirstFourInARow() {
            var round = inProgress().withKingPlayed(aider(), false);
            round = award(round, ma());
            round = award(round, aider());
            round = award(round, ma());
            round = award(round, aider());

            assertThat(round.madePrimeres()).isTrue();
        }

        @Test
        void shouldNotReportPrimeresWhenTheOpponentsTookOneOfTheFirstFour() {
            var round = inProgress().withKingPlayed(aider(), false);
            round = award(round, ma());
            round = award(round, opponent());
            round = award(round, ma());
            round = award(round, ma());

            assertThat(round.madePrimeres()).isFalse();
        }

        @Test
        void shouldPlayPastFiveWhileTodoIsStillReachable() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = award(round, ma());
            }

            assertThat(round.isComplete())
                .as("five is a decision point, not the end, while todo is live")
                .isFalse();
            assertThat(round.goingSideBasas()).isEqualTo(5);
        }

        @Test
        void shouldSettleAtFiveOnceTodoIsOutOfReach() {
            var round = inProgress().withKingPlayed(aider(), false);
            round = award(round, opponent());
            for (var i = 0; i < 5; i++) {
                round = award(round, ma());
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.madeTodo()).isFalse();
            assertThat(round.completedBasas()).hasSize(6);
        }

        @Test
        void shouldPauseOnTheTodoCallAtFiveWithACleanSweep() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = awardWithoutDeciding(round, ma());
            }

            assertThat(round.isWaitingForTodo()).isTrue();
            assertThat(round.todoCaller())
                .as("the one who goes decides for the side")
                .contains(ma());
            assertThat(round.currentBasa()).as("play is paused").isEmpty();
        }

        @Test
        void shouldEndTheRoundWhenTheGoingSideBanksTheWin() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = awardWithoutDeciding(round, ma());
            }

            round = round.withTodoDeclined(ma());

            assertThat(round.isComplete()).isTrue();
            assertThat(round.todoCalled()).isFalse();
            assertThat(round.completedBasas()).hasSize(5);
            assertThat(round.result().orElseThrow()).isInstanceOf(RoundResult.GoingSideWon.class);
        }

        @Test
        void shouldPlayOnWhenTheGoingSideCallsTodo() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = awardWithoutDeciding(round, ma());
            }

            round = round.withTodoCalled(ma());

            assertThat(round.isInProgress()).isTrue();
            assertThat(round.todoCalled()).isTrue();
            assertThat(round.currentBasa().orElseThrow().basaNumber()).isEqualTo(6);
        }

        @Test
        void shouldRecordACalledTodoThatWasMissed() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = awardWithoutDeciding(round, ma());
            }
            round = round.withTodoCalled(ma());
            round = award(round, opponent());

            assertThat(round.isComplete()).as("the win was already theirs").isTrue();
            assertThat(round.todoCalled()).isTrue();
            assertThat(round.madeTodo()).isFalse();
            assertThat(round.result().orElseThrow()).isInstanceOf(RoundResult.GoingSideWon.class);
        }

        @Test
        void shouldRefuseTheCallFromAnyoneElse() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < 5; i++) {
                round = awardWithoutDeciding(round, ma());
            }
            final var paused = round;

            assertThatThrownBy(() -> paused.withTodoCalled(opponent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("call");
        }

        @Test
        void shouldReportTodoWhenTheGoingSideTakesEveryBasa() {
            var round = inProgress().withKingPlayed(aider(), false);
            for (var i = 0; i < Round.MAX_BASAS; i++) {
                round = award(round, ma());
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.madeTodo()).isTrue();
            assertThat(round.completedBasas()).hasSize(Round.MAX_BASAS);
        }
    }

    @Nested
    @DisplayName("Es primer rei aida")
    class FirstKingCall {

        @Test
        void shouldRecordTheCall() {
            final var round = inProgress().withFirstKingCalled();

            assertThat(round.firstKingCalled()).isTrue();
        }

        @Test
        void shouldRefuseTheCallOnceTheSideIsDecided() {
            final var round = inProgress().withKingPlayed(aider(), false);

            assertThatThrownBy(round::withFirstKingCalled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");
        }
    }

    // === Helpers ===

    private Map<PlayerId, List<Card>> deal() {
        return TestDeal.forPlayers(players)
            .give(players.get(0), Card.espadilla())
            .give(players.get(1), Card.basto())
            .hands();
    }

    private Map<PlayerId, List<Card>> fourKingDeal(PlayerId holder) {
        return TestDeal.forPlayers(players)
            .give(holder,
                new Card(Suit.COPAS, Rank.REY),
                new Card(Suit.OROS, Rank.REY),
                new Card(Suit.ESPADAS, Rank.REY),
                new Card(Suit.BASTOS, Rank.REY))
            .hands();
    }

    private Round inProgress() {
        var round = Round.start(1, ma(), players, deal(), DEADLINE);
        for (final var player : players) {
            round = round.withSoledadPass(player);
        }
        return round.withTrump(TRUMP);
    }

    /** Plays a basa and awards it, leaving any todo window open for the test to answer. */
    private Round awardWithoutDeciding(Round round, PlayerId winner) {
        if (round.isComplete() || round.isWaitingForTodo()) {
            return round;
        }
        for (var seat = 0; seat < 5; seat++) {
            final var current = round.currentPlayer().orElseThrow();
            final var hand = new ArrayList<>(round.getHand(current));
            round = round.withCardPlayed(current, hand.get(0), NOW);
        }
        return round.completeBasa(winner);
    }

    /** Plays out a basa with whatever cards are to hand and awards it to the given player. */
    private Round award(Round round, PlayerId winner) {
        if (round.isComplete()) {
            return round;
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
        return round;
    }
}
