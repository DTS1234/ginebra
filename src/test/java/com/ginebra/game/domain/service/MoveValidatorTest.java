package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoveValidatorTest {

    private final MoveValidator validator = new MoveValidator(new CardRankingService());

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    @Nested
    class FirstCardOfBasa {

        @Test
        void shouldAllowAnyCardAsFirstCard() {
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.OROS, Rank.TRES),
                card(Suit.BASTOS, Rank.CINCO)
            );

            final var result = validator.validate(hand, card(Suit.OROS, Rank.TRES), Suit.COPAS, Optional.empty());

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldAllowSpecialCardAsFirstCard() {
            final var hand = List.of(
                Card.espadilla(),
                card(Suit.COPAS, Rank.REY)
            );

            final var result = validator.validate(hand, Card.espadilla(), Suit.COPAS, Optional.empty());

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }
    }

    @Nested
    class MustFollowSuit {

        @Test
        void shouldRequireFollowingSuitWhenPlayerHasLedSuit() {
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.COPAS, Rank.CABALLO),
                card(Suit.OROS, Rank.TRES)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            final var result = validator.validate(hand, card(Suit.OROS, Rank.TRES), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            final var invalid = (MoveValidation.Invalid) result;
            assertThat(invalid.code()).isEqualTo("MUST_FOLLOW_SUIT");
        }

        @Test
        void shouldAllowPlayingLedSuitCard() {
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.COPAS, Rank.CABALLO),
                card(Suit.OROS, Rank.TRES)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            final var result = validator.validate(hand, card(Suit.COPAS, Rank.REY), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldAllowAnyCardWhenPlayerHasNoLedSuit() {
            final var hand = List.of(
                card(Suit.OROS, Rank.REY),
                card(Suit.BASTOS, Rank.CINCO),
                card(Suit.ESPADAS, Rank.TRES)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            // Playing OROS when COPAS is led but player has no COPAS
            final var result = validator.validate(hand, card(Suit.OROS, Rank.REY), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldAllowFallarWhenNoLedSuit() {
            final var hand = List.of(
                card(Suit.OROS, Rank.REY),
                card(Suit.BASTOS, Rank.CINCO)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            // Playing trump (BASTOS) when no COPAS - fallar
            final var result = validator.validate(hand, card(Suit.BASTOS, Rank.CINCO), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }
    }

    @Nested
    class SpecialCards {

        @Test
        void shouldRejectEspadillaWhenPlayerCanStillFollowTheLedSuit() {
            final var hand = List.of(
                Card.espadilla(),
                card(Suit.COPAS, Rank.REY),
                card(Suit.COPAS, Rank.CABALLO)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            // The Espadilla is a trump, so playing it here is fallar - only open when void.
            final var result = validator.validate(hand, Card.espadilla(), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_FOLLOW_SUIT");
        }

        @Test
        void shouldRejectBastoWhenPlayerCanStillFollowTheLedSuit() {
            final var hand = List.of(
                Card.basto(),
                card(Suit.COPAS, Rank.REY),
                card(Suit.COPAS, Rank.CABALLO)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            final var result = validator.validate(hand, Card.basto(), Suit.BASTOS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_FOLLOW_SUIT");
        }

        @Test
        void shouldAllowSpecialCardAsFallarWhenVoidInTheLedSuit() {
            final var hand = List.of(
                Card.basto(),
                card(Suit.OROS, Rank.TRES)
            );
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            final var result = validator.validate(hand, Card.basto(), Suit.ESPADAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldRequireFollowingAPlainSuitRatherThanPlayingTheBasto() {
            // Oros is led and the player holds a low oro plus the Basto. The Basto is a
            // trump, and fallar is open only to a player with none of the led suit.
            final var hand = List.of(
                card(Suit.OROS, Rank.TRES),
                Card.basto(),
                card(Suit.ESPADAS, Rank.CINCO)
            );
            final var ledCard = card(Suit.OROS, Rank.REY);

            final var result = validator.validate(hand, Card.basto(), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_FOLLOW_SUIT");
        }

        @Test
        void shouldNotCountEspadillaAsEspadasForFollowing() {
            // Player has only Espadilla as an ESPADAS card + other cards
            final var hand = List.of(
                Card.espadilla(),
                card(Suit.COPAS, Rank.TRES),
                card(Suit.OROS, Rank.CINCO)
            );
            final var ledCard = card(Suit.ESPADAS, Rank.REY);

            // Player should NOT be forced to follow ESPADAS since Espadilla is special
            final var result = validator.validate(hand, card(Suit.COPAS, Rank.TRES), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldNotCountBastoAsBastosForFollowing() {
            // Player has only Basto as a BASTOS card + other cards
            final var hand = List.of(
                Card.basto(),
                card(Suit.COPAS, Rank.TRES),
                card(Suit.OROS, Rank.CINCO)
            );
            final var ledCard = card(Suit.BASTOS, Rank.REY);

            // Player should NOT be forced to follow BASTOS since Basto is special
            final var result = validator.validate(hand, card(Suit.COPAS, Rank.TRES), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldRequireFollowingEspadasWhenHasRegularEspadasCards() {
            // Player has regular Espadas cards (not just Espadilla)
            final var hand = List.of(
                Card.espadilla(),
                card(Suit.ESPADAS, Rank.REY),
                card(Suit.COPAS, Rank.TRES)
            );
            final var ledCard = card(Suit.ESPADAS, Rank.CABALLO);

            // Must play the regular Espadas card; the Espadilla is a trump, not an Espada.
            final var result = validator.validate(hand, card(Suit.COPAS, Rank.TRES), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_FOLLOW_SUIT");
        }
    }

    @Nested
    class SpecialCardLeads {

        @Test
        void shouldTreatEspadillaLeadAsEffectiveTrumpSuit() {
            // Espadilla leads, trump is COPAS -> effective led suit is COPAS
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.OROS, Rank.TRES)
            );
            final var ledCard = Card.espadilla();

            // An Espadilla lead is a trump lead, and COPAS in hand is a trump
            final var result = validator.validate(hand, card(Suit.OROS, Rank.TRES), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_PLAY_TRUMP");
        }

        @Test
        void shouldTreatBastoLeadAsEffectiveTrumpSuit() {
            // Basto leads, trump is OROS -> effective led suit is OROS
            final var hand = List.of(
                card(Suit.OROS, Rank.CABALLO),
                card(Suit.COPAS, Rank.TRES)
            );
            final var ledCard = Card.basto();

            // A Basto lead is a trump lead, and OROS in hand is a trump
            final var result = validator.validate(hand, card(Suit.COPAS, Rank.TRES), Suit.OROS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_PLAY_TRUMP");
        }

        @Test
        void shouldAllowAnyCardWhenSpecialLeadsAndNoTrumpInHand() {
            // Espadilla leads, trump is COPAS, but player has no COPAS
            final var hand = List.of(
                card(Suit.OROS, Rank.REY),
                card(Suit.BASTOS, Rank.CINCO)
            );
            final var ledCard = Card.espadilla();

            final var result = validator.validate(hand, card(Suit.OROS, Rank.REY), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldAllowTrumpCardWhenSpecialLeads() {
            // Espadilla leads, trump is COPAS, player follows with COPAS
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.OROS, Rank.TRES)
            );
            final var ledCard = Card.espadilla();

            final var result = validator.validate(hand, card(Suit.COPAS, Rank.REY), Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Valid.class);
        }
    }

    /**
     * rules-source.md §4.5: when a trump is led everyone must play a trump if they hold
     * one, except that a special card outranking the card led may be kept back.
     */
    @Nested
    class TrumpLead {

        // Trump is COPAS throughout, so the Manilla is the 7 de copes.
        private static final Suit TRUMP = Suit.COPAS;
        private static final Card MANILLA = new Card(Suit.COPAS, Rank.SIETE);
        private static final Card PLAIN_TRUMP = new Card(Suit.COPAS, Rank.CUATRO);
        private static final Card DISCARD = new Card(Suit.OROS, Rank.TRES);

        private MoveValidation play(Card cardToPlay, Card ledCard, Card... hand) {
            return validator.validate(List.of(hand), cardToPlay, TRUMP, Optional.of(ledCard));
        }

        private void assertMustTrump(MoveValidation result) {
            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("MUST_PLAY_TRUMP");
        }

        @Test
        void espadillaLeadShouldExemptNothing() {
            // "Si un trumfa d'espadilla, és obligatori tirar trumfo si en tens."
            assertMustTrump(play(DISCARD, Card.espadilla(), Card.basto(), DISCARD));
            assertMustTrump(play(DISCARD, Card.espadilla(), MANILLA, DISCARD));
            assertMustTrump(play(DISCARD, Card.espadilla(), PLAIN_TRUMP, DISCARD));
        }

        @Test
        void manillaLeadShouldExemptOnlyTheEspadilla() {
            // "…però es qui té l'espadilla se la juga si vol."
            assertThat(play(DISCARD, MANILLA, Card.espadilla(), DISCARD))
                .isInstanceOf(MoveValidation.Valid.class);
            assertMustTrump(play(DISCARD, MANILLA, Card.basto(), DISCARD));
            assertMustTrump(play(DISCARD, MANILLA, Card.espadilla(), PLAIN_TRUMP, DISCARD));
        }

        @Test
        void bastoLeadShouldExemptTheEspadillaAndTheManilla() {
            // "Es qui tenen espadilla i manilla, se la poden jugar si volen."
            assertThat(play(DISCARD, Card.basto(), Card.espadilla(), MANILLA, DISCARD))
                .isInstanceOf(MoveValidation.Valid.class);
            assertMustTrump(play(DISCARD, Card.basto(), MANILLA, PLAIN_TRUMP, DISCARD));
        }

        @Test
        void plainTrumpLeadShouldExemptAllThreeSpecials() {
            // "Es qui tinguen ses tres cartes (espadilla, manilla, basto) se les poden jugar si volen."
            assertThat(play(DISCARD, PLAIN_TRUMP, Card.espadilla(), MANILLA, Card.basto(), DISCARD))
                .isInstanceOf(MoveValidation.Valid.class);
            assertMustTrump(play(DISCARD, PLAIN_TRUMP, Card.basto(), new Card(Suit.COPAS, Rank.SEIS), DISCARD));
        }

        @Test
        void shouldNotExemptAnOrdinaryTrumpThatHappensToBeatTheCardLed() {
            // The privilege belongs to the three specials, not to any card that outranks.
            assertMustTrump(play(DISCARD, new Card(Suit.COPAS, Rank.CINCO), new Card(Suit.COPAS, Rank.REY), DISCARD));
        }

        @Test
        void shouldAllowWithholdableSpecialToBePlayedAnyway() {
            // The exemption is permission to keep it back, not a ban on playing it.
            assertThat(play(Card.espadilla(), MANILLA, Card.espadilla(), DISCARD))
                .isInstanceOf(MoveValidation.Valid.class);
        }

        @Test
        void shouldAllowAnyCardWhenHoldingNoTrumpAtAll() {
            assertThat(play(DISCARD, PLAIN_TRUMP, DISCARD, new Card(Suit.BASTOS, Rank.CINCO)))
                .isInstanceOf(MoveValidation.Valid.class);
        }
    }

    @Nested
    class CardNotInHand {

        @Test
        void shouldRejectCardNotInHand() {
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.OROS, Rank.TRES)
            );
            final var notInHand = card(Suit.BASTOS, Rank.CINCO);

            final var result = validator.validate(hand, notInHand, Suit.COPAS, Optional.empty());

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("INVALID_CARD");
        }

        @Test
        void shouldRejectCardNotInHandEvenWithMatchingLedSuit() {
            final var hand = List.of(
                card(Suit.COPAS, Rank.REY),
                card(Suit.OROS, Rank.TRES)
            );
            final var notInHand = card(Suit.COPAS, Rank.CABALLO);
            final var ledCard = card(Suit.COPAS, Rank.DOS);

            final var result = validator.validate(hand, notInHand, Suit.COPAS, Optional.of(ledCard));

            assertThat(result).isInstanceOf(MoveValidation.Invalid.class);
            assertThat(((MoveValidation.Invalid) result).code()).isEqualTo("INVALID_CARD");
        }
    }

    @Nested
    class EffectiveLedSuit {

        @ParameterizedTest
        @EnumSource(Suit.class)
        void shouldReturnTrumpWhenEspadillaLeads(Suit trump) {
            final var result = MoveValidator.effectiveLedSuit(Card.espadilla(), trump);

            assertThat(result).isEqualTo(trump);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void shouldReturnTrumpWhenBastoLeads(Suit trump) {
            final var result = MoveValidator.effectiveLedSuit(Card.basto(), trump);

            assertThat(result).isEqualTo(trump);
        }

        @Test
        void shouldReturnCardSuitWhenRegularCardLeads() {
            final var result = MoveValidator.effectiveLedSuit(card(Suit.OROS, Rank.REY), Suit.COPAS);

            assertThat(result).isEqualTo(Suit.OROS);
        }

        @ParameterizedTest
        @EnumSource(Suit.class)
        void shouldReturnCardSuitForNonSpecialCards(Suit suit) {
            final var result = MoveValidator.effectiveLedSuit(card(suit, Rank.REY), Suit.COPAS);

            assertThat(result).isEqualTo(suit);
        }
    }

    @Nested
    class NullValidation {

        @Test
        void shouldRejectNullHand() {
            assertThatThrownBy(() -> validator.validate(null, card(Suit.COPAS, Rank.REY), Suit.COPAS, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hand must not be null");
        }

        @Test
        void shouldRejectNullCard() {
            final var hand = List.of(card(Suit.COPAS, Rank.REY));

            assertThatThrownBy(() -> validator.validate(hand, null, Suit.COPAS, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cardToPlay must not be null");
        }

        @Test
        void shouldRejectNullTrumpSuit() {
            final var hand = List.of(card(Suit.COPAS, Rank.REY));

            assertThatThrownBy(() -> validator.validate(hand, card(Suit.COPAS, Rank.REY), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trumpSuit must not be null");
        }

        @Test
        void shouldRejectNullFirstCardOptional() {
            final var hand = List.of(card(Suit.COPAS, Rank.REY));

            assertThatThrownBy(() -> validator.validate(hand, card(Suit.COPAS, Rank.REY), Suit.COPAS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstCardInBasa must not be null");
        }

        @Test
        void effectiveLedSuitShouldRejectNullFirstCard() {
            assertThatThrownBy(() -> MoveValidator.effectiveLedSuit(null, Suit.COPAS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstCard must not be null");
        }

        @Test
        void effectiveLedSuitShouldRejectNullTrumpSuit() {
            assertThatThrownBy(() -> MoveValidator.effectiveLedSuit(card(Suit.COPAS, Rank.REY), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trumpSuit must not be null");
        }
    }
}
