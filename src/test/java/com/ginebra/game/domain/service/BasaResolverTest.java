package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.*;
import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasaResolverTest {

    private final CardRankingService cardRankingService = new CardRankingService();
    private final BasaResolver resolver = new BasaResolver(cardRankingService);

    private static final PlayerId P1 = PlayerId.generate();
    private static final PlayerId P2 = PlayerId.generate();
    private static final PlayerId P3 = PlayerId.generate();
    private static final PlayerId P4 = PlayerId.generate();
    private static final PlayerId P5 = PlayerId.generate();

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    private static PlayedCard played(PlayerId player, Suit suit, Rank rank) {
        return PlayedCard.of(player, card(suit, rank), Instant.now());
    }

    private static PlayedCard played(PlayerId player, Card card) {
        return PlayedCard.of(player, card, Instant.now());
    }

    private Basa buildBasa(PlayedCard... cards) {
        var basa = Basa.start(1, cards[0].playerId());
        for (final var card : cards) {
            basa = basa.withCard(card);
        }
        return basa;
    }

    @Nested
    class TrumpWins {

        @Test
        void shouldResolveWinnerWhenTrumpBeatsNonTrump() {
            // Trump is COPAS. P1 leads with OROS, P3 plays COPAS (trump)
            final var basa = buildBasa(
                played(P1, Suit.OROS, Rank.REY),
                played(P2, Suit.OROS, Rank.CABALLO),
                played(P3, Suit.COPAS, Rank.DOS),     // Trump - lowest but still beats non-trump
                played(P4, Suit.OROS, Rank.SOTA),
                played(P5, Suit.OROS, Rank.TRES)
            );

            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P3);
        }

        @Test
        void shouldResolveHigherTrumpBeatsLowerTrump() {
            // Trump is COPAS. P2 and P4 both play trump (refallar)
            final var basa = buildBasa(
                played(P1, Suit.OROS, Rank.REY),
                played(P2, Suit.COPAS, Rank.CINCO),    // Trump - rank 11
                played(P3, Suit.OROS, Rank.TRES),
                played(P4, Suit.COPAS, Rank.CABALLO),  // Trump - rank 6, higher
                played(P5, Suit.OROS, Rank.CUATRO)
            );

            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P4);
        }
    }

    @Nested
    class SpecialCards {

        @Test
        void shouldResolveEspadillaAsWinner() {
            final var basa = buildBasa(
                played(P1, Suit.COPAS, Rank.REY),
                played(P2, Card.espadilla()),
                played(P3, Suit.COPAS, Rank.SIETE),   // Manilla when COPAS is trump
                played(P4, Suit.COPAS, Rank.CABALLO),
                played(P5, Suit.OROS, Rank.REY)
            );

            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P2);
        }

        @Test
        void shouldResolveBastoBeatsRegularTrump() {
            final var basa = buildBasa(
                played(P1, Suit.OROS, Rank.REY),
                played(P2, Suit.COPAS, Rank.REY),      // Trump
                played(P3, Card.basto()),               // Basto - rank 3, beats regular trump
                played(P4, Suit.COPAS, Rank.CABALLO),  // Trump
                played(P5, Suit.OROS, Rank.TRES)
            );

            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P3);
        }

        @Test
        void shouldResolveEspadillaBeatsManilla() {
            // Even manilla (rank 2) loses to espadilla (rank 1)
            final var basa = buildBasa(
                played(P1, Suit.COPAS, Rank.SIETE),    // Manilla when COPAS trump
                played(P2, Card.espadilla()),
                played(P3, Card.basto()),
                played(P4, Suit.COPAS, Rank.REY),
                played(P5, Suit.OROS, Rank.REY)
            );

            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P2);
        }
    }

    @Nested
    class LedSuitWins {

        @Test
        void shouldResolveLedSuitHighestWhenNoTrumpPlayed() {
            // Trump is BASTOS, but no one plays it. Led suit is OROS.
            final var basa = buildBasa(
                played(P1, Suit.OROS, Rank.TRES),
                played(P2, Suit.OROS, Rank.REY),       // Highest OROS
                played(P3, Suit.OROS, Rank.CABALLO),
                played(P4, Suit.COPAS, Rank.REY),      // Not led suit, not trump
                played(P5, Suit.OROS, Rank.SOTA)
            );

            final var winner = resolver.resolveWinner(basa, Suit.BASTOS);

            assertThat(winner).isEqualTo(P2);
        }

        @Test
        void shouldIgnoreHigherNonLedSuitCards() {
            // Led suit is COPAS. P4 plays OROS REY but it can't win
            final var basa = buildBasa(
                played(P1, Suit.COPAS, Rank.TRES),
                played(P2, Suit.COPAS, Rank.CINCO),
                played(P3, Suit.ESPADAS, Rank.REY),    // Non-led, non-trump
                played(P4, Suit.OROS, Rank.REY),       // Non-led, non-trump
                played(P5, Suit.COPAS, Rank.CUATRO)
            );

            // Copas numbers run 2 down to 7, so among 3, 5 and 4 the 3 is highest (spec 2.4)
            final var winner = resolver.resolveWinner(basa, Suit.BASTOS);

            assertThat(winner).isEqualTo(P1);
        }
    }

    @Nested
    class SpecialCardLeads {

        @Test
        void shouldUseEffectiveLedSuitWhenEspadillaLeads() {
            // Espadilla leads, trump is COPAS -> effective led suit is COPAS
            final var basa = buildBasa(
                played(P1, Card.espadilla()),
                played(P2, Suit.COPAS, Rank.REY),      // Trump/effective led suit
                played(P3, Suit.COPAS, Rank.CABALLO),  // Trump/effective led suit
                played(P4, Suit.OROS, Rank.REY),       // Not led suit, not trump
                played(P5, Suit.COPAS, Rank.SOTA)      // Trump/effective led suit
            );

            // P1 wins with Espadilla (always highest)
            final var winner = resolver.resolveWinner(basa, Suit.COPAS);

            assertThat(winner).isEqualTo(P1);
        }

        @Test
        void shouldResolveCorrectlyWhenBastoLeads() {
            // Basto leads, trump is OROS -> effective led suit is OROS
            final var basa = buildBasa(
                played(P1, Card.basto()),
                played(P2, Suit.OROS, Rank.REY),
                played(P3, Suit.OROS, Rank.CABALLO),
                played(P4, Suit.COPAS, Rank.REY),      // Not led suit
                played(P5, Suit.OROS, Rank.SOTA)
            );

            // P1 wins with Basto (rank 3, beats regular trump cards)
            final var winner = resolver.resolveWinner(basa, Suit.OROS);

            assertThat(winner).isEqualTo(P1);
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectBasaWithFewerThanFiveCards() {
            var basa = Basa.start(1, P1);
            basa = basa.withCard(played(P1, Suit.COPAS, Rank.REY));
            final var incompleteBasa = basa;

            assertThatThrownBy(() -> resolver.resolveWinner(incompleteBasa, Suit.COPAS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have 5 cards");
        }

        @Test
        void shouldRejectAlreadyCompleteBasa() {
            final var basa = buildBasa(
                played(P1, Suit.COPAS, Rank.REY),
                played(P2, Suit.COPAS, Rank.CABALLO),
                played(P3, Suit.COPAS, Rank.SOTA),
                played(P4, Suit.OROS, Rank.REY),
                played(P5, Suit.OROS, Rank.CABALLO)
            );
            final var completedBasa = basa.complete(P1);

            assertThatThrownBy(() -> resolver.resolveWinner(completedBasa, Suit.COPAS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already complete");
        }

        @Test
        void shouldRejectNullBasa() {
            assertThatThrownBy(() -> resolver.resolveWinner(null, Suit.COPAS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("basa must not be null");
        }

        @Test
        void shouldRejectNullTrumpSuit() {
            final var basa = buildBasa(
                played(P1, Suit.COPAS, Rank.REY),
                played(P2, Suit.COPAS, Rank.CABALLO),
                played(P3, Suit.COPAS, Rank.SOTA),
                played(P4, Suit.OROS, Rank.REY),
                played(P5, Suit.OROS, Rank.CABALLO)
            );

            assertThatThrownBy(() -> resolver.resolveWinner(basa, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trumpSuit must not be null");
        }

        @Test
        void shouldRejectNullCardRankingServiceInConstructor() {
            assertThatThrownBy(() -> new BasaResolver(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cardRankingService must not be null");
        }
    }
}
