package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.ginebra.game.domain.model.Rank.*;
import static com.ginebra.game.domain.model.Suit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardRankingServiceTest {

    private CardRankingService service;

    @BeforeEach
    void setUp() {
        service = new CardRankingService();
    }

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    @Nested
    class GetManilla {

        @Test
        void shouldReturn7ForCopas() {
            final var manilla = service.getManilla(COPAS);

            assertThat(manilla).isEqualTo(card(COPAS, SIETE));
        }

        @Test
        void shouldReturn7ForOros() {
            final var manilla = service.getManilla(OROS);

            assertThat(manilla).isEqualTo(card(OROS, SIETE));
        }

        @Test
        void shouldReturn2ForEspadas() {
            final var manilla = service.getManilla(ESPADAS);

            assertThat(manilla).isEqualTo(card(ESPADAS, DOS));
        }

        @Test
        void shouldReturn2ForBastos() {
            final var manilla = service.getManilla(BASTOS);

            assertThat(manilla).isEqualTo(card(BASTOS, DOS));
        }

        @Test
        void shouldRejectNullTrump() {
            assertThatThrownBy(() -> service.getManilla(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trump must not be null");
        }
    }

    @Nested
    class SpecialCards {

        @ParameterizedTest
        @EnumSource(Suit.class)
        void espadillaShouldBeatAnyCardRegardlessOfTrump(Suit trump) {
            final var espadilla = Card.espadilla();
            final var king = card(trump, REY);

            final var winner = service.higher(trump, trump, espadilla, king);

            assertThat(winner).isEqualTo(espadilla);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void espadillaShouldBeatManillaRegardlessOfTrump(Suit trump) {
            final var espadilla = Card.espadilla();
            final var manilla = service.getManilla(trump);

            final var winner = service.higher(trump, trump, espadilla, manilla);

            assertThat(winner).isEqualTo(espadilla);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void espadillaShouldBeatBastoRegardlessOfTrump(Suit trump) {
            final var espadilla = Card.espadilla();
            final var basto = Card.basto();

            final var winner = service.higher(trump, trump, espadilla, basto);

            assertThat(winner).isEqualTo(espadilla);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void manillaShouldBeatBastoRegardlessOfTrump(Suit trump) {
            final var manilla = service.getManilla(trump);
            final var basto = Card.basto();

            final var winner = service.higher(trump, trump, manilla, basto);

            assertThat(winner).isEqualTo(manilla);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void bastoShouldBeatKingOfTrump(Suit trump) {
            final var basto = Card.basto();
            final var king = card(trump, REY);

            final var winner = service.higher(trump, trump, basto, king);

            assertThat(winner).isEqualTo(basto);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void bastoShouldBeatAnyNonSpecialCard(Suit trump) {
            final var basto = Card.basto();
            // Use a non-trump, non-special card
            final var otherSuit = trump == COPAS ? OROS : COPAS;
            final var king = card(otherSuit, REY);

            final var winner = service.higher(trump, otherSuit, basto, king);

            assertThat(winner).isEqualTo(basto);
        }
    }

    @Nested
    class TrumpVsNonTrump {

        @Test
        void lowestTrumpShouldBeatHighestNonTrump() {
            final var trump = COPAS;
            final var ledSuit = OROS;
            final var lowestTrump = card(COPAS, SEIS); // Low trump card
            final var highestNonTrump = card(OROS, REY); // King of led suit

            final var winner = service.higher(trump, ledSuit, lowestTrump, highestNonTrump);

            assertThat(winner).isEqualTo(lowestTrump);
        }

        @Test
        void trumpShouldBeatAnyNonLedSuitCard() {
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var trumpCard = card(ESPADAS, CUATRO);
            final var otherCard = card(OROS, REY);

            final var winner = service.higher(trump, ledSuit, trumpCard, otherCard);

            assertThat(winner).isEqualTo(trumpCard);
        }

        @Test
        void higherTrumpShouldBeatLowerTrump() {
            final var trump = COPAS;
            // When COPAS is trump: King (rank 5) beats Dos (rank 8)
            final var king = card(COPAS, REY);
            final var dos = card(COPAS, DOS);

            final var winner = service.higher(trump, trump, king, dos);

            assertThat(winner).isEqualTo(king);
        }
    }

    @Nested
    class TrumpSuitRankingsForCopasOros {

        @ParameterizedTest
        @MethodSource("copasOrosTrumpRankings")
        void shouldRankCopasOrosTrumpCorrectly(Suit trump, Card higher, Card lower) {
            final var winner = service.higher(trump, trump, higher, lower);

            assertThat(winner).isEqualTo(higher);
        }

        static Stream<Arguments> copasOrosTrumpRankings() {
            return Stream.of(
                // COPAS trump rankings (after special cards)
                Arguments.of(COPAS, card(COPAS, AS), card(COPAS, REY)),      // Ace beats King
                Arguments.of(COPAS, card(COPAS, REY), card(COPAS, CABALLO)), // King beats Horse
                Arguments.of(COPAS, card(COPAS, CABALLO), card(COPAS, SOTA)),// Horse beats Jack
                Arguments.of(COPAS, card(COPAS, SOTA), card(COPAS, DOS)),    // Jack beats 2
                Arguments.of(COPAS, card(COPAS, DOS), card(COPAS, TRES)),    // 2 beats 3
                Arguments.of(COPAS, card(COPAS, TRES), card(COPAS, CUATRO)), // 3 beats 4
                Arguments.of(COPAS, card(COPAS, CUATRO), card(COPAS, CINCO)),// 4 beats 5
                Arguments.of(COPAS, card(COPAS, CINCO), card(COPAS, SEIS)),  // 5 beats 6
                // 7 is Manilla (rank 2), tested separately

                // OROS trump rankings (same pattern)
                Arguments.of(OROS, card(OROS, AS), card(OROS, REY)),
                Arguments.of(OROS, card(OROS, REY), card(OROS, CABALLO)),
                Arguments.of(OROS, card(OROS, CABALLO), card(OROS, SOTA)),
                Arguments.of(OROS, card(OROS, SOTA), card(OROS, DOS))
            );
        }

        @Test
        void manilla7ShouldBeatAceOfTrumpInCopas() {
            final var manilla = card(COPAS, SIETE);
            final var ace = card(COPAS, AS);

            final var winner = service.higher(COPAS, COPAS, manilla, ace);

            assertThat(winner).isEqualTo(manilla);
        }

        @Test
        void manilla7ShouldBeatAceOfTrumpInOros() {
            final var manilla = card(OROS, SIETE);
            final var ace = card(OROS, AS);

            final var winner = service.higher(OROS, OROS, manilla, ace);

            assertThat(winner).isEqualTo(manilla);
        }
    }

    @Nested
    class TrumpSuitRankingsForEspadasBastos {

        @ParameterizedTest
        @MethodSource("espadasBastosTrumpRankings")
        void shouldRankEspadasBastosTrumpCorrectly(Suit trump, Card higher, Card lower) {
            final var winner = service.higher(trump, trump, higher, lower);

            assertThat(winner).isEqualTo(higher);
        }

        static Stream<Arguments> espadasBastosTrumpRankings() {
            return Stream.of(
                // ESPADAS trump rankings (after special cards, note: 2 is Manilla)
                Arguments.of(ESPADAS, card(ESPADAS, REY), card(ESPADAS, CABALLO)),  // King beats Horse
                Arguments.of(ESPADAS, card(ESPADAS, CABALLO), card(ESPADAS, SOTA)), // Horse beats Jack
                Arguments.of(ESPADAS, card(ESPADAS, SOTA), card(ESPADAS, SIETE)),   // Jack beats 7
                Arguments.of(ESPADAS, card(ESPADAS, SIETE), card(ESPADAS, SEIS)),   // 7 beats 6
                Arguments.of(ESPADAS, card(ESPADAS, SEIS), card(ESPADAS, CINCO)),   // 6 beats 5
                Arguments.of(ESPADAS, card(ESPADAS, CINCO), card(ESPADAS, CUATRO)), // 5 beats 4
                Arguments.of(ESPADAS, card(ESPADAS, CUATRO), card(ESPADAS, TRES)),  // 4 beats 3
                // 2 is Manilla (rank 2), Ace is Espadilla (rank 1) - tested separately

                // BASTOS trump rankings (same pattern)
                Arguments.of(BASTOS, card(BASTOS, REY), card(BASTOS, CABALLO)),
                Arguments.of(BASTOS, card(BASTOS, CABALLO), card(BASTOS, SOTA)),
                Arguments.of(BASTOS, card(BASTOS, SOTA), card(BASTOS, SIETE)),
                Arguments.of(BASTOS, card(BASTOS, SIETE), card(BASTOS, SEIS))
            );
        }

        @Test
        void manilla2ShouldBeatKingOfTrumpInEspadas() {
            final var manilla = card(ESPADAS, DOS);
            final var king = card(ESPADAS, REY);

            final var winner = service.higher(ESPADAS, ESPADAS, manilla, king);

            assertThat(winner).isEqualTo(manilla);
        }

        @Test
        void manilla2ShouldBeatKingOfTrumpInBastos() {
            final var manilla = card(BASTOS, DOS);
            final var king = card(BASTOS, REY);

            final var winner = service.higher(BASTOS, BASTOS, manilla, king);

            assertThat(winner).isEqualTo(manilla);
        }
    }

    @Nested
    class NonTrumpRankings {

        @ParameterizedTest
        @MethodSource("nonTrumpRankings")
        void shouldRankNonTrumpCardsCorrectly(Card higher, Card lower) {
            // Use a different trump so these are non-trump cards
            final var trump = ESPADAS;
            final var ledSuit = COPAS;

            final var winner = service.higher(trump, ledSuit, higher, lower);

            assertThat(winner).isEqualTo(higher);
        }

        static Stream<Arguments> nonTrumpRankings() {
            return Stream.of(
                Arguments.of(card(COPAS, REY), card(COPAS, CABALLO)),     // King beats Horse
                Arguments.of(card(COPAS, CABALLO), card(COPAS, SOTA)),    // Horse beats Jack
                Arguments.of(card(COPAS, SOTA), card(COPAS, AS)),         // Jack beats Ace
                Arguments.of(card(COPAS, AS), card(COPAS, SIETE)),        // Ace beats 7
                Arguments.of(card(COPAS, SIETE), card(COPAS, SEIS)),      // 7 beats 6
                Arguments.of(card(COPAS, SEIS), card(COPAS, CINCO)),      // 6 beats 5
                Arguments.of(card(COPAS, CINCO), card(COPAS, CUATRO)),    // 5 beats 4
                Arguments.of(card(COPAS, CUATRO), card(COPAS, TRES)),     // 4 beats 3
                Arguments.of(card(COPAS, TRES), card(COPAS, DOS))         // 3 beats 2
            );
        }
    }

    @Nested
    class LedSuitRules {

        @Test
        void ledSuitShouldBeatNonLedNonTrump() {
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var ledCard = card(COPAS, DOS);     // Lowest led suit card
            final var otherCard = card(OROS, REY);    // King of other suit

            final var winner = service.higher(trump, ledSuit, ledCard, otherCard);

            assertThat(winner).isEqualTo(ledCard);
        }

        @Test
        void higherLedSuitShouldBeatLowerLedSuit() {
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var king = card(COPAS, REY);
            final var dos = card(COPAS, DOS);

            final var winner = service.higher(trump, ledSuit, king, dos);

            assertThat(winner).isEqualTo(king);
        }

        @Test
        void nonLedNonTrumpShouldLoseToLedSuit() {
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var ledCard = card(COPAS, DOS);
            final var otherKing = card(OROS, REY);

            final var winner = service.higher(trump, ledSuit, otherKing, ledCard);

            assertThat(winner).isEqualTo(ledCard);
        }

        @Test
        void whenBothNonLedNonTrumpHigherRankWins() {
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var dos = card(OROS, DOS);
            final var king = card(BASTOS, REY);

            // Neither can beat led suit, but King has better non-trump rank
            // In a real basa, neither wins (led suit or trump would win)
            // But for comparison purposes, higher rank is "better"
            final var winner = service.higher(trump, ledSuit, dos, king);

            assertThat(winner).isEqualTo(king);
        }
    }

    @Nested
    class FindWinner {

        @Test
        void shouldFindWinnerInSimpleBasa() {
            final var trump = COPAS;
            final var ledSuit = OROS;
            final var cards = List.of(
                card(OROS, REY),     // Led with King
                card(OROS, CABALLO), // Horse
                card(OROS, SOTA),    // Jack
                card(OROS, AS),      // Ace
                card(OROS, DOS)      // 2
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            assertThat(winner).isEqualTo(card(OROS, REY));
        }

        @Test
        void shouldFindTrumpWinner() {
            final var trump = COPAS;
            final var ledSuit = OROS;
            final var cards = List.of(
                card(OROS, REY),     // Led with King
                card(COPAS, DOS),    // Trump (fallar)
                card(OROS, CABALLO), // Horse
                card(COPAS, TRES),   // Higher trump (refallar)
                card(OROS, SOTA)     // Jack
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            // Trump 3 beats Trump 2 (3 is rank 9, 2 is rank 8 in COPAS)
            assertThat(winner).isEqualTo(card(COPAS, DOS));
        }

        @Test
        void shouldFindEspadillaWinner() {
            final var trump = COPAS;
            final var ledSuit = OROS;
            final var cards = List.of(
                card(OROS, REY),
                Card.espadilla(),
                card(COPAS, SIETE), // Manilla
                Card.basto(),
                card(COPAS, REY)
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            assertThat(winner).isEqualTo(Card.espadilla());
        }

        @Test
        void shouldFindManillaWinnerWhenNoEspadilla() {
            final var trump = COPAS;
            final var ledSuit = OROS;
            final var cards = List.of(
                card(OROS, REY),
                card(COPAS, REY),
                card(COPAS, SIETE), // Manilla
                Card.basto(),
                card(COPAS, AS)
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            assertThat(winner).isEqualTo(card(COPAS, SIETE));
        }

        @Test
        void shouldRejectEmptyList() {
            assertThatThrownBy(() -> service.findWinner(COPAS, COPAS, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playedCards must not be empty");
        }

        @Test
        void shouldRejectNullList() {
            assertThatThrownBy(() -> service.findWinner(COPAS, COPAS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playedCards must not be null");
        }

        @Test
        void shouldHandleSingleCard() {
            final var trump = COPAS;
            final var singleCard = card(OROS, DOS);
            final var cards = List.of(singleCard);

            final var winner = service.findWinner(trump, OROS, cards);

            assertThat(winner).isEqualTo(singleCard);
        }
    }

    @Nested
    class HigherMethodValidation {

        @Test
        void shouldRejectNullTrump() {
            assertThatThrownBy(() -> service.higher(null, COPAS, card(COPAS, REY), card(COPAS, AS)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trump must not be null");
        }

        @Test
        void shouldRejectNullLedSuit() {
            assertThatThrownBy(() -> service.higher(COPAS, null, card(COPAS, REY), card(COPAS, AS)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ledSuit must not be null");
        }

        @Test
        void shouldRejectNullCard1() {
            assertThatThrownBy(() -> service.higher(COPAS, COPAS, null, card(COPAS, AS)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card1 must not be null");
        }

        @Test
        void shouldRejectNullCard2() {
            assertThatThrownBy(() -> service.higher(COPAS, COPAS, card(COPAS, REY), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card2 must not be null");
        }
    }

    @Nested
    class RealGameScenarios {

        @Test
        void scenarioFromSpec_BasaExample() {
            // From spec.md Appendix B example:
            // Trump: COPAS, Led: BASTOS
            // P2: 6♣, P3: 5♥ (trump), P4: 7♣, P5: Horse♣, P1: Horse♥ (trump)
            // Winner should be P1's Horse of COPAS (refallar)
            final var trump = COPAS;
            final var ledSuit = BASTOS;
            final var cards = List.of(
                card(BASTOS, SEIS),     // P2
                card(COPAS, CINCO),     // P3 - trump (fallar)
                card(BASTOS, SIETE),    // P4
                card(BASTOS, CABALLO),  // P5
                card(COPAS, CABALLO)    // P1 - trump (refallar)
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            // Horse of COPAS (rank 6) beats 5 of COPAS (rank 11)
            assertThat(winner).isEqualTo(card(COPAS, CABALLO));
        }

        @Test
        void scenarioAllSpecialCards() {
            // When Espadilla, Manilla, and Basto are all played
            final var trump = COPAS;
            final var ledSuit = COPAS;
            final var cards = List.of(
                card(COPAS, SIETE),  // Manilla
                Card.espadilla(),    // Espadilla
                Card.basto()         // Basto
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            assertThat(winner).isEqualTo(Card.espadilla());
        }

        @Test
        void scenarioNoTrumpPlayed() {
            // All follow led suit, highest wins
            final var trump = ESPADAS;
            final var ledSuit = COPAS;
            final var cards = List.of(
                card(COPAS, SOTA),
                card(COPAS, CABALLO),
                card(COPAS, REY),
                card(COPAS, AS),
                card(COPAS, DOS)
            );

            final var winner = service.findWinner(trump, ledSuit, cards);

            assertThat(winner).isEqualTo(card(COPAS, REY));
        }
    }
}
