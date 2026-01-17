package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayedCardTest {

    @Nested
    class Creation {

        @Test
        void shouldCreateWithValidData() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var playedAt = Instant.now();

            final var playedCard = new PlayedCard(playerId, card, playedAt);

            assertThat(playedCard.playerId()).isEqualTo(playerId);
            assertThat(playedCard.card()).isEqualTo(card);
            assertThat(playedCard.playedAt()).isEqualTo(playedAt);
        }

        @Test
        void shouldRejectNullPlayerId() {
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var playedAt = Instant.now();

            assertThatThrownBy(() -> new PlayedCard(null, card, playedAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerId must not be null");
        }

        @Test
        void shouldRejectNullCard() {
            final var playerId = PlayerId.generate();
            final var playedAt = Instant.now();

            assertThatThrownBy(() -> new PlayedCard(playerId, null, playedAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card must not be null");
        }

        @Test
        void shouldRejectNullPlayedAt() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.COPAS, Rank.REY);

            assertThatThrownBy(() -> new PlayedCard(playerId, card, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playedAt must not be null");
        }
    }

    @Nested
    class FactoryMethod {

        @Test
        void ofShouldCreatePlayedCard() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.OROS, Rank.CABALLO);
            final var playedAt = Instant.now();

            final var playedCard = PlayedCard.of(playerId, card, playedAt);

            assertThat(playedCard.playerId()).isEqualTo(playerId);
            assertThat(playedCard.card()).isEqualTo(card);
            assertThat(playedCard.playedAt()).isEqualTo(playedAt);
        }
    }

    @Nested
    class Equality {

        @Test
        void shouldBeEqualForSameValues() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var playedAt = Instant.parse("2024-01-01T12:00:00Z");

            final var playedCard1 = new PlayedCard(playerId, card, playedAt);
            final var playedCard2 = new PlayedCard(playerId, card, playedAt);

            assertThat(playedCard1).isEqualTo(playedCard2);
            assertThat(playedCard1.hashCode()).isEqualTo(playedCard2.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentPlayer() {
            final var card = new Card(Suit.COPAS, Rank.REY);
            final var playedAt = Instant.now();

            final var playedCard1 = new PlayedCard(PlayerId.generate(), card, playedAt);
            final var playedCard2 = new PlayedCard(PlayerId.generate(), card, playedAt);

            assertThat(playedCard1).isNotEqualTo(playedCard2);
        }

        @Test
        void shouldNotBeEqualForDifferentCard() {
            final var playerId = PlayerId.generate();
            final var playedAt = Instant.now();

            final var playedCard1 = new PlayedCard(playerId, new Card(Suit.COPAS, Rank.REY), playedAt);
            final var playedCard2 = new PlayedCard(playerId, new Card(Suit.OROS, Rank.REY), playedAt);

            assertThat(playedCard1).isNotEqualTo(playedCard2);
        }

        @Test
        void shouldNotBeEqualForDifferentTime() {
            final var playerId = PlayerId.generate();
            final var card = new Card(Suit.COPAS, Rank.REY);

            final var playedCard1 = new PlayedCard(playerId, card, Instant.parse("2024-01-01T12:00:00Z"));
            final var playedCard2 = new PlayedCard(playerId, card, Instant.parse("2024-01-01T13:00:00Z"));

            assertThat(playedCard1).isNotEqualTo(playedCard2);
        }
    }
}
