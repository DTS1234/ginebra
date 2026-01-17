package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasaTest {

    @Nested
    class Start {

        @Test
        void shouldCreateWithCorrectBasaNumber() {
            final var startingPlayer = PlayerId.generate();

            final var basa = Basa.start(1, startingPlayer);

            assertThat(basa.basaNumber()).isEqualTo(1);
        }

        @Test
        void shouldCreateWithCorrectStartingPlayer() {
            final var startingPlayer = PlayerId.generate();

            final var basa = Basa.start(1, startingPlayer);

            assertThat(basa.startingPlayer()).isEqualTo(startingPlayer);
        }

        @Test
        void shouldCreateWithEmptyCards() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThat(basa.cardsPlayed()).isEmpty();
            assertThat(basa.cardCount()).isZero();
        }

        @Test
        void shouldCreateWithInProgressStatus() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThat(basa.status()).isEqualTo(BasaStatus.IN_PROGRESS);
            assertThat(basa.isInProgress()).isTrue();
            assertThat(basa.isComplete()).isFalse();
        }

        @Test
        void shouldCreateWithNoWinner() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThat(basa.winner()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
        void shouldAcceptValidBasaNumbers(int basaNumber) {
            final var basa = Basa.start(basaNumber, PlayerId.generate());

            assertThat(basa.basaNumber()).isEqualTo(basaNumber);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 9, 10, 100})
        void shouldRejectInvalidBasaNumbers(int basaNumber) {
            assertThatThrownBy(() -> Basa.start(basaNumber, PlayerId.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basaNumber must be between 1 and 8");
        }

        @Test
        void shouldRejectNullStartingPlayer() {
            assertThatThrownBy(() -> Basa.start(1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("startingPlayer must not be null");
        }
    }

    @Nested
    class WithCard {

        @Test
        void shouldAddCardToEmptyBasa() {
            final var basa = Basa.start(1, PlayerId.generate());
            final var playedCard = createPlayedCard(Suit.COPAS, Rank.REY);

            final var result = basa.withCard(playedCard);

            assertThat(result.cardsPlayed()).hasSize(1);
            assertThat(result.cardsPlayed().get(0)).isEqualTo(playedCard);
        }

        @Test
        void shouldAddMultipleCards() {
            var basa = Basa.start(1, PlayerId.generate());
            final var card1 = createPlayedCard(Suit.COPAS, Rank.REY);
            final var card2 = createPlayedCard(Suit.COPAS, Rank.CABALLO);
            final var card3 = createPlayedCard(Suit.OROS, Rank.SOTA);

            basa = basa.withCard(card1);
            basa = basa.withCard(card2);
            basa = basa.withCard(card3);

            assertThat(basa.cardsPlayed()).hasSize(3);
            assertThat(basa.cardCount()).isEqualTo(3);
        }

        @Test
        void shouldReturnNewInstance() {
            final var original = Basa.start(1, PlayerId.generate());
            final var playedCard = createPlayedCard(Suit.COPAS, Rank.REY);

            final var result = original.withCard(playedCard);

            assertThat(result).isNotSameAs(original);
        }

        @Test
        void shouldNotModifyOriginal() {
            final var original = Basa.start(1, PlayerId.generate());
            final var playedCard = createPlayedCard(Suit.COPAS, Rank.REY);

            original.withCard(playedCard);

            assertThat(original.cardsPlayed()).isEmpty();
        }

        @Test
        void shouldRejectNullPlayedCard() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThatThrownBy(() -> basa.withCard(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playedCard must not be null");
        }

        @Test
        void shouldRejectSixthCard() {
            var basa = Basa.start(1, PlayerId.generate());
            for (var i = 0; i < 5; i++) {
                basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.values()[i]));
            }
            final var fullBasa = basa;
            final var extraCard = createPlayedCard(Suit.OROS, Rank.REY);

            assertThatThrownBy(() -> fullBasa.withCard(extraCard))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot add card");
        }

        @Test
        void shouldRejectCardWhenComplete() {
            var basa = Basa.start(1, PlayerId.generate());
            for (var i = 0; i < 5; i++) {
                basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.values()[i]));
            }
            final var completedBasa = basa.complete(PlayerId.generate());
            final var extraCard = createPlayedCard(Suit.OROS, Rank.REY);

            assertThatThrownBy(() -> completedBasa.withCard(extraCard))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot add card");
        }
    }

    @Nested
    class Complete {

        @Test
        void shouldMarkAsComplete() {
            final var basa = createBasaWithFiveCards();
            final var winner = PlayerId.generate();

            final var result = basa.complete(winner);

            assertThat(result.status()).isEqualTo(BasaStatus.COMPLETE);
            assertThat(result.isComplete()).isTrue();
            assertThat(result.isInProgress()).isFalse();
        }

        @Test
        void shouldSetWinner() {
            final var basa = createBasaWithFiveCards();
            final var winner = PlayerId.generate();

            final var result = basa.complete(winner);

            assertThat(result.winner()).contains(winner);
        }

        @Test
        void shouldReturnNewInstance() {
            final var original = createBasaWithFiveCards();
            final var winner = PlayerId.generate();

            final var result = original.complete(winner);

            assertThat(result).isNotSameAs(original);
        }

        @Test
        void shouldNotModifyOriginal() {
            final var original = createBasaWithFiveCards();
            final var winner = PlayerId.generate();

            original.complete(winner);

            assertThat(original.status()).isEqualTo(BasaStatus.IN_PROGRESS);
            assertThat(original.winner()).isEmpty();
        }

        @Test
        void shouldRejectNullWinner() {
            final var basa = createBasaWithFiveCards();

            assertThatThrownBy(() -> basa.complete(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("winnerId must not be null");
        }

        @Test
        void shouldRejectWhenNotFiveCards() {
            var basa = Basa.start(1, PlayerId.generate());
            basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.REY));
            basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.CABALLO));
            final var incompleteBasa = basa;
            final var winner = PlayerId.generate();

            assertThatThrownBy(() -> incompleteBasa.complete(winner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete basa: need 5 cards, have 2");
        }

        @Test
        void shouldRejectWhenAlreadyComplete() {
            final var basa = createBasaWithFiveCards();
            final var completedBasa = basa.complete(PlayerId.generate());
            final var anotherWinner = PlayerId.generate();

            assertThatThrownBy(() -> completedBasa.complete(anotherWinner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Basa is already complete");
        }
    }

    @Nested
    class LedSuit {

        @Test
        void shouldReturnEmptyWhenNoCards() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThat(basa.ledSuit()).isEmpty();
        }

        @Test
        void shouldReturnFirstCardSuit() {
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(createPlayedCard(Suit.OROS, Rank.REY))
                .withCard(createPlayedCard(Suit.COPAS, Rank.CABALLO));

            assertThat(basa.ledSuit()).contains(Suit.OROS);
        }

        @Test
        void shouldReturnEspadillaSuitWhenLed() {
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(createPlayedCard(Suit.ESPADAS, Rank.AS));

            assertThat(basa.ledSuit()).contains(Suit.ESPADAS);
        }
    }

    @Nested
    class QueryMethods {

        @Test
        void canAcceptCardShouldReturnTrueWhenInProgress() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThat(basa.canAcceptCard()).isTrue();
        }

        @Test
        void canAcceptCardShouldReturnFalseWhenComplete() {
            final var basa = createBasaWithFiveCards().complete(PlayerId.generate());

            assertThat(basa.canAcceptCard()).isFalse();
        }

        @Test
        void canAcceptCardShouldReturnFalseWhenFiveCards() {
            final var basa = createBasaWithFiveCards();

            assertThat(basa.canAcceptCard()).isFalse();
        }

        @Test
        void cardCountShouldReturnCorrectCount() {
            var basa = Basa.start(1, PlayerId.generate());
            assertThat(basa.cardCount()).isZero();

            basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.REY));
            assertThat(basa.cardCount()).isEqualTo(1);

            basa = basa.withCard(createPlayedCard(Suit.OROS, Rank.CABALLO));
            assertThat(basa.cardCount()).isEqualTo(2);
        }

        @Test
        void cardsShouldReturnOnlyCards() {
            final var card1 = new Card(Suit.COPAS, Rank.REY);
            final var card2 = new Card(Suit.OROS, Rank.CABALLO);
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(PlayedCard.of(PlayerId.generate(), card1, Instant.now()))
                .withCard(PlayedCard.of(PlayerId.generate(), card2, Instant.now()));

            final var cards = basa.cards();

            assertThat(cards).containsExactly(card1, card2);
        }
    }

    @Nested
    class FindPlayedCard {

        @Test
        void shouldFindMatchingCard() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var playedCard = PlayedCard.of(playerId, card, Instant.now());
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(playedCard)
                .withCard(createPlayedCard(Suit.OROS, Rank.CABALLO));

            final var result = basa.findPlayedCard(card);

            assertThat(result).isEqualTo(playedCard);
            assertThat(result.playerId()).isEqualTo(playerId);
        }

        @Test
        void shouldThrowWhenCardNotFound() {
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(createPlayedCard(Suit.COPAS, Rank.REY));
            final var notFoundCard = new Card(Suit.OROS, Rank.CABALLO);

            assertThatThrownBy(() -> basa.findPlayedCard(notFoundCard))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Card not found in basa");
        }

        @Test
        void shouldRejectNullCard() {
            final var basa = Basa.start(1, PlayerId.generate());

            assertThatThrownBy(() -> basa.findPlayedCard(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card must not be null");
        }
    }

    @Nested
    class Immutability {

        @Test
        void cardsPlayedShouldBeImmutable() {
            final var basa = Basa.start(1, PlayerId.generate())
                .withCard(createPlayedCard(Suit.COPAS, Rank.REY));

            final var cards = basa.cardsPlayed();

            assertThatThrownBy(() -> cards.add(createPlayedCard(Suit.OROS, Rank.CABALLO)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void withCardShouldNotModifyOriginal() {
            final var original = Basa.start(1, PlayerId.generate());
            final var originalCardCount = original.cardCount();

            original.withCard(createPlayedCard(Suit.COPAS, Rank.REY));

            assertThat(original.cardCount()).isEqualTo(originalCardCount);
        }

        @Test
        void completeShouldNotModifyOriginal() {
            final var original = createBasaWithFiveCards();

            original.complete(PlayerId.generate());

            assertThat(original.isInProgress()).isTrue();
            assertThat(original.winner()).isEmpty();
        }
    }

    @Nested
    class RealGameScenarios {

        @Test
        void shouldHandleFullBasaFlow() {
            final var player1 = PlayerId.generate();
            final var player2 = PlayerId.generate();
            final var player3 = PlayerId.generate();
            final var player4 = PlayerId.generate();
            final var player5 = PlayerId.generate();

            var basa = Basa.start(1, player1);

            // Each player plays a card
            basa = basa.withCard(PlayedCard.of(player1, new Card(Suit.COPAS, Rank.REY), Instant.now()));
            basa = basa.withCard(PlayedCard.of(player2, new Card(Suit.COPAS, Rank.CABALLO), Instant.now()));
            basa = basa.withCard(PlayedCard.of(player3, new Card(Suit.COPAS, Rank.SOTA), Instant.now()));
            basa = basa.withCard(PlayedCard.of(player4, new Card(Suit.OROS, Rank.REY), Instant.now()));
            basa = basa.withCard(PlayedCard.of(player5, new Card(Suit.BASTOS, Rank.AS), Instant.now()));

            assertThat(basa.cardCount()).isEqualTo(5);
            assertThat(basa.ledSuit()).contains(Suit.COPAS);
            assertThat(basa.isInProgress()).isTrue();

            // Complete with winner (player1 had highest Copas)
            basa = basa.complete(player1);

            assertThat(basa.isComplete()).isTrue();
            assertThat(basa.winner()).contains(player1);
        }
    }

    // === Helper Methods ===

    private PlayedCard createPlayedCard(Suit suit, Rank rank) {
        return PlayedCard.of(PlayerId.generate(), new Card(suit, rank), Instant.now());
    }

    private Basa createBasaWithFiveCards() {
        var basa = Basa.start(1, PlayerId.generate());
        for (var i = 0; i < 5; i++) {
            basa = basa.withCard(createPlayedCard(Suit.COPAS, Rank.values()[i]));
        }
        return basa;
    }
}
