package com.ginebra.game.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateDeckWith40Cards() {
            final var deck = Deck.create();

            assertThat(deck.size()).isEqualTo(40);
        }

        @Test
        void shouldContainAllSuitsAndRanks() {
            final var deck = Deck.create();

            for (final var suit : Suit.values()) {
                for (final var rank : Rank.values()) {
                    final var card = new Card(suit, rank);
                    assertThat(deck.cards()).contains(card);
                }
            }
        }

        @Test
        void shouldContainExactlyOneOfEachCard() {
            final var deck = Deck.create();
            final var uniqueCards = new HashSet<>(deck.cards());

            assertThat(uniqueCards).hasSize(40);
            assertThat(deck.cards()).hasSize(40);
        }

        @Test
        void shouldNotBeEmpty() {
            final var deck = Deck.create();

            assertThat(deck.isEmpty()).isFalse();
        }
    }

    @Nested
    class Shuffle {

        @Test
        void shuffleShouldReturnNewDeck() {
            final var deck = Deck.create();
            final var shuffled = deck.shuffle(new Random(42));

            assertThat(shuffled).isNotSameAs(deck);
        }

        @Test
        void shuffleShouldPreserveAllCards() {
            final var deck = Deck.create();
            final var shuffled = deck.shuffle(new Random(42));

            assertThat(shuffled.size()).isEqualTo(40);
            assertThat(new HashSet<>(shuffled.cards()))
                .isEqualTo(new HashSet<>(deck.cards()));
        }

        @Test
        void shuffleShouldRandomizeOrder() {
            final var deck = Deck.create();
            final var shuffled = deck.shuffle(new Random(42));

            // With 40 cards, probability of same order is essentially zero
            assertThat(shuffled.cards()).isNotEqualTo(deck.cards());
        }

        @Test
        void shuffleWithSameSeedShouldProduceSameOrder() {
            final var deck = Deck.create();
            final var shuffled1 = deck.shuffle(new Random(123));
            final var shuffled2 = deck.shuffle(new Random(123));

            assertThat(shuffled1.cards()).isEqualTo(shuffled2.cards());
        }

        @Test
        void shuffleShouldRejectNullRandom() {
            final var deck = Deck.create();

            assertThatThrownBy(() -> deck.shuffle(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("random must not be null");
        }
    }

    @Nested
    class Deal {

        @Test
        void dealTo5PlayersShouldGive8CardsEach() {
            final var deck = Deck.create();
            final var hands = deck.deal(5);

            assertThat(hands).hasSize(5);
            for (final var hand : hands) {
                assertThat(hand).hasSize(8);
            }
        }

        @Test
        void dealShouldUseAllCards() {
            final var deck = Deck.create();
            final var hands = deck.deal(5);

            final var allDealtCards = new HashSet<Card>();
            for (final var hand : hands) {
                allDealtCards.addAll(hand);
            }

            assertThat(allDealtCards).hasSize(40);
            assertThat(allDealtCards).isEqualTo(new HashSet<>(deck.cards()));
        }

        @Test
        void dealShouldDistributeCardsEvenly() {
            final var deck = Deck.create();
            final var hands = deck.deal(4); // 40 / 4 = 10 cards each

            assertThat(hands).hasSize(4);
            for (final var hand : hands) {
                assertThat(hand).hasSize(10);
            }
        }

        @Test
        void dealShouldGiveUniqueCardsToEachPlayer() {
            final var deck = Deck.create().shuffle(new Random(42));
            final var hands = deck.deal(5);

            for (var i = 0; i < hands.size(); i++) {
                for (var j = i + 1; j < hands.size(); j++) {
                    for (final var card : hands.get(i)) {
                        assertThat(hands.get(j)).doesNotContain(card);
                    }
                }
            }
        }

        @Test
        void dealShouldRejectZeroPlayers() {
            final var deck = Deck.create();

            assertThatThrownBy(() -> deck.deal(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerCount must be positive");
        }

        @Test
        void dealShouldRejectNegativePlayers() {
            final var deck = Deck.create();

            assertThatThrownBy(() -> deck.deal(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerCount must be positive");
        }

        @Test
        void dealShouldRejectUnevenDistribution() {
            final var deck = Deck.create();

            assertThatThrownBy(() -> deck.deal(3)) // 40 / 3 doesn't divide evenly
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deal");
        }

        @Test
        void dealShouldReturnImmutableHands() {
            final var deck = Deck.create();
            final var hands = deck.deal(5);

            assertThatThrownBy(() -> hands.get(0).add(Card.espadilla()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class FindEspadillaHolder {

        @Test
        void shouldFindEspadillaHolder() {
            final var deck = Deck.create().shuffle(new Random(42));
            final var hands = deck.deal(5);

            final var holderIndex = Deck.findEspadillaHolder(hands);

            assertThat(holderIndex).isBetween(0, 4);
            assertThat(hands.get(holderIndex)).contains(Card.espadilla());
        }

        @Test
        void shouldFindCorrectHolder() {
            final var deck = Deck.create(); // Unshuffled - Espadilla is in predictable position
            final var hands = deck.deal(5);

            final var holderIndex = Deck.findEspadillaHolder(hands);

            // Verify the holder actually has the Espadilla
            final var holderHand = hands.get(holderIndex);
            assertThat(holderHand.stream().anyMatch(Card::isEspadilla)).isTrue();
        }

        @Test
        void shouldRejectNullHands() {
            assertThatThrownBy(() -> Deck.findEspadillaHolder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hands must not be null");
        }
    }

    @Nested
    class Immutability {

        @Test
        void cardsShouldReturnImmutableList() {
            final var deck = Deck.create();

            assertThatThrownBy(() -> deck.cards().add(Card.espadilla()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void originalDeckShouldNotChangeAfterShuffle() {
            final var deck = Deck.create();
            final var originalCards = deck.cards();

            deck.shuffle(new Random(42));

            assertThat(deck.cards()).isEqualTo(originalCards);
        }
    }
}
