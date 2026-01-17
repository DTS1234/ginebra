package com.ginebra.game.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateCardWithSuitAndRank() {
            final var card = new Card(Suit.COPAS, Rank.REY);

            assertThat(card.suit()).isEqualTo(Suit.COPAS);
            assertThat(card.rank()).isEqualTo(Rank.REY);
        }

        @Test
        void shouldRejectNullSuit() {
            assertThatThrownBy(() -> new Card(null, Rank.REY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("suit must not be null");
        }

        @Test
        void shouldRejectNullRank() {
            assertThatThrownBy(() -> new Card(Suit.COPAS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rank must not be null");
        }
    }

    @Nested
    class FactoryMethods {

        @Test
        void espadillaShouldCreateAceOfEspadas() {
            final var espadilla = Card.espadilla();

            assertThat(espadilla.suit()).isEqualTo(Suit.ESPADAS);
            assertThat(espadilla.rank()).isEqualTo(Rank.AS);
        }

        @Test
        void bastoShouldCreateAceOfBastos() {
            final var basto = Card.basto();

            assertThat(basto.suit()).isEqualTo(Suit.BASTOS);
            assertThat(basto.rank()).isEqualTo(Rank.AS);
        }
    }

    @Nested
    class SpecialCards {

        @Test
        void isEspadillaShouldReturnTrueForAceOfEspadas() {
            final var card = new Card(Suit.ESPADAS, Rank.AS);

            assertThat(card.isEspadilla()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("nonEspadillaCards")
        void isEspadillaShouldReturnFalseForOtherCards(Card card) {
            assertThat(card.isEspadilla()).isFalse();
        }

        static Stream<Card> nonEspadillaCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.AS),
                new Card(Suit.OROS, Rank.AS),
                new Card(Suit.BASTOS, Rank.AS),
                new Card(Suit.ESPADAS, Rank.REY),
                new Card(Suit.ESPADAS, Rank.SIETE)
            );
        }

        @Test
        void isBastoShouldReturnTrueForAceOfBastos() {
            final var card = new Card(Suit.BASTOS, Rank.AS);

            assertThat(card.isBasto()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("nonBastoCards")
        void isBastoShouldReturnFalseForOtherCards(Card card) {
            assertThat(card.isBasto()).isFalse();
        }

        static Stream<Card> nonBastoCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.AS),
                new Card(Suit.OROS, Rank.AS),
                new Card(Suit.ESPADAS, Rank.AS),
                new Card(Suit.BASTOS, Rank.REY),
                new Card(Suit.BASTOS, Rank.DOS)
            );
        }

        @Test
        void isSpecialShouldReturnTrueForEspadilla() {
            final var espadilla = Card.espadilla();

            assertThat(espadilla.isSpecial()).isTrue();
        }

        @Test
        void isSpecialShouldReturnTrueForBasto() {
            final var basto = Card.basto();

            assertThat(basto.isSpecial()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("nonSpecialCards")
        void isSpecialShouldReturnFalseForRegularCards(Card card) {
            assertThat(card.isSpecial()).isFalse();
        }

        static Stream<Card> nonSpecialCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.AS),
                new Card(Suit.OROS, Rank.AS),
                new Card(Suit.COPAS, Rank.REY),
                new Card(Suit.ESPADAS, Rank.REY),
                new Card(Suit.BASTOS, Rank.SIETE)
            );
        }
    }

    @Nested
    class Manilla {

        @ParameterizedTest
        @MethodSource("potentialManillaCards")
        void isPotentialManillaShouldReturnTrueForManillaCandidates(Card card) {
            assertThat(card.isPotentialManilla()).isTrue();
        }

        static Stream<Card> potentialManillaCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.SIETE),
                new Card(Suit.OROS, Rank.SIETE),
                new Card(Suit.ESPADAS, Rank.DOS),
                new Card(Suit.BASTOS, Rank.DOS)
            );
        }

        @ParameterizedTest
        @MethodSource("nonPotentialManillaCards")
        void isPotentialManillaShouldReturnFalseForNonCandidates(Card card) {
            assertThat(card.isPotentialManilla()).isFalse();
        }

        static Stream<Card> nonPotentialManillaCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.DOS),
                new Card(Suit.OROS, Rank.DOS),
                new Card(Suit.ESPADAS, Rank.SIETE),
                new Card(Suit.BASTOS, Rank.SIETE),
                new Card(Suit.COPAS, Rank.REY),
                Card.espadilla(),
                Card.basto()
            );
        }

        @ParameterizedTest
        @MethodSource("manillaScenarios")
        void isManillaShouldIdentifyManillaForTrumpSuit(
            Card card,
            Suit trumpSuit,
            boolean expectedResult
        ) {
            assertThat(card.isManilla(trumpSuit)).isEqualTo(expectedResult);
        }

        static Stream<Arguments> manillaScenarios() {
            return Stream.of(
                // Copas trump: 7 of Copas is manilla
                Arguments.of(new Card(Suit.COPAS, Rank.SIETE), Suit.COPAS, true),
                Arguments.of(new Card(Suit.COPAS, Rank.SIETE), Suit.OROS, false),
                Arguments.of(new Card(Suit.OROS, Rank.SIETE), Suit.COPAS, false),

                // Oros trump: 7 of Oros is manilla
                Arguments.of(new Card(Suit.OROS, Rank.SIETE), Suit.OROS, true),
                Arguments.of(new Card(Suit.OROS, Rank.SIETE), Suit.COPAS, false),

                // Espadas trump: 2 of Espadas is manilla
                Arguments.of(new Card(Suit.ESPADAS, Rank.DOS), Suit.ESPADAS, true),
                Arguments.of(new Card(Suit.ESPADAS, Rank.DOS), Suit.BASTOS, false),
                Arguments.of(new Card(Suit.ESPADAS, Rank.SIETE), Suit.ESPADAS, false),

                // Bastos trump: 2 of Bastos is manilla
                Arguments.of(new Card(Suit.BASTOS, Rank.DOS), Suit.BASTOS, true),
                Arguments.of(new Card(Suit.BASTOS, Rank.DOS), Suit.ESPADAS, false),

                // 7s are not manilla for Espadas/Bastos trump
                Arguments.of(new Card(Suit.ESPADAS, Rank.SIETE), Suit.ESPADAS, false),
                Arguments.of(new Card(Suit.BASTOS, Rank.SIETE), Suit.BASTOS, false),

                // 2s are not manilla for Copas/Oros trump
                Arguments.of(new Card(Suit.COPAS, Rank.DOS), Suit.COPAS, false),
                Arguments.of(new Card(Suit.OROS, Rank.DOS), Suit.OROS, false)
            );
        }

        @Test
        void isManillaShouldRejectNullTrumpSuit() {
            final var card = new Card(Suit.COPAS, Rank.SIETE);

            assertThatThrownBy(() -> card.isManilla(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trumpSuit must not be null");
        }
    }

    @Nested
    class King {

        @ParameterizedTest
        @EnumSource(Suit.class)
        void isKingShouldReturnTrueForAllKings(Suit suit) {
            final var king = new Card(suit, Rank.REY);

            assertThat(king.isKing()).isTrue();
        }

        @ParameterizedTest
        @MethodSource("nonKingCards")
        void isKingShouldReturnFalseForNonKings(Card card) {
            assertThat(card.isKing()).isFalse();
        }

        static Stream<Card> nonKingCards() {
            return Stream.of(
                new Card(Suit.COPAS, Rank.CABALLO),
                new Card(Suit.OROS, Rank.SOTA),
                new Card(Suit.ESPADAS, Rank.AS),
                new Card(Suit.BASTOS, Rank.SIETE)
            );
        }
    }

    @Nested
    class Equality {

        @Test
        void cardsWithSameSuitAndRankShouldBeEqual() {
            final var card1 = new Card(Suit.COPAS, Rank.REY);
            final var card2 = new Card(Suit.COPAS, Rank.REY);

            assertThat(card1).isEqualTo(card2);
            assertThat(card1.hashCode()).isEqualTo(card2.hashCode());
        }

        @Test
        void cardsWithDifferentSuitShouldNotBeEqual() {
            final var card1 = new Card(Suit.COPAS, Rank.REY);
            final var card2 = new Card(Suit.OROS, Rank.REY);

            assertThat(card1).isNotEqualTo(card2);
        }

        @Test
        void cardsWithDifferentRankShouldNotBeEqual() {
            final var card1 = new Card(Suit.COPAS, Rank.REY);
            final var card2 = new Card(Suit.COPAS, Rank.CABALLO);

            assertThat(card1).isNotEqualTo(card2);
        }

        @Test
        void factoryMethodsShouldProduceEqualCards() {
            final var espadilla1 = Card.espadilla();
            final var espadilla2 = new Card(Suit.ESPADAS, Rank.AS);

            assertThat(espadilla1).isEqualTo(espadilla2);
        }
    }
}
