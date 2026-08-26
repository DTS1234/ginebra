package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.ginebra.game.domain.model.Rank.*;
import static com.ginebra.game.domain.model.Suit.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transcribes the four ranking tables of spec 2.4 and checks the engine orders every
 * column exactly that way.
 *
 * The point of writing the tables out longhand is that they are the requirement: if
 * CardRankingService and the spec ever drift again, this fails and names the pair of
 * cards that disagree.
 *
 * Two things the spec's tables get wrong on their own terms and that are corrected here,
 * per its own "Note on Manilla": the Manilla appears once, at position 2, and does not
 * reappear at the bottom of the trump column.
 *
 * The second test transcribes the order as Tàrbena players stated it (2026-08-26), which
 * settled D-6 - where the Ace ranks in a suit that is not trump. It is deliberately a
 * separate authority from the spec tables above: if the two ever disagree, both fail and
 * the players win.
 */
@DisplayName("Card order matches spec 2.4")
class SpecCardOrderTest {

    private final CardRankingService service = new CardRankingService();

    /**
     * Every column of every table, strongest card first.
     */
    static Stream<Arguments> specColumns() {
        return Stream.of(
            // --- When COPAS goes ---
            column(COPAS, COPAS, List.of(
                card(ESPADAS, AS), card(COPAS, SIETE), card(BASTOS, AS),
                card(COPAS, AS), card(COPAS, REY), card(COPAS, CABALLO), card(COPAS, SOTA),
                card(COPAS, DOS), card(COPAS, TRES), card(COPAS, CUATRO),
                card(COPAS, CINCO), card(COPAS, SEIS))),
            column(COPAS, OROS, nonTrumpWithAce(OROS)),
            column(COPAS, ESPADAS, nonTrumpWithoutAce(ESPADAS)),
            column(COPAS, BASTOS, nonTrumpWithoutAce(BASTOS)),

            // --- When OROS goes ---
            column(OROS, OROS, List.of(
                card(ESPADAS, AS), card(OROS, SIETE), card(BASTOS, AS),
                card(OROS, AS), card(OROS, REY), card(OROS, CABALLO), card(OROS, SOTA),
                card(OROS, DOS), card(OROS, TRES), card(OROS, CUATRO),
                card(OROS, CINCO), card(OROS, SEIS))),
            column(OROS, COPAS, nonTrumpWithAce(COPAS)),
            column(OROS, ESPADAS, nonTrumpWithoutAce(ESPADAS)),
            column(OROS, BASTOS, nonTrumpWithoutAce(BASTOS)),

            // --- When ESPADAS goes ---
            column(ESPADAS, ESPADAS, List.of(
                card(ESPADAS, AS), card(ESPADAS, DOS), card(BASTOS, AS),
                card(ESPADAS, REY), card(ESPADAS, CABALLO), card(ESPADAS, SOTA),
                card(ESPADAS, SIETE), card(ESPADAS, SEIS), card(ESPADAS, CINCO),
                card(ESPADAS, CUATRO), card(ESPADAS, TRES))),
            column(ESPADAS, COPAS, nonTrumpWithAce(COPAS)),
            column(ESPADAS, OROS, nonTrumpWithAce(OROS)),
            column(ESPADAS, BASTOS, nonTrumpWithoutAce(BASTOS)),

            // --- When BASTOS goes ---
            column(BASTOS, BASTOS, List.of(
                card(ESPADAS, AS), card(BASTOS, DOS), card(BASTOS, AS),
                card(BASTOS, REY), card(BASTOS, CABALLO), card(BASTOS, SOTA),
                card(BASTOS, SIETE), card(BASTOS, SEIS), card(BASTOS, CINCO),
                card(BASTOS, CUATRO), card(BASTOS, TRES))),
            column(BASTOS, COPAS, nonTrumpWithAce(COPAS)),
            column(BASTOS, OROS, nonTrumpWithAce(OROS)),
            column(BASTOS, ESPADAS, nonTrumpWithoutAce(ESPADAS))
        );
    }

    @ParameterizedTest(name = "{1} when {0} is trump")
    @MethodSource("specColumns")
    void shouldRankEveryColumnAsTheSpecTablesDo(Suit trump, Suit suit, List<Card> strongestFirst) {
        for (var i = 0; i < strongestFirst.size() - 1; i++) {
            final var stronger = strongestFirst.get(i);
            final var weaker = strongestFirst.get(i + 1);

            assertThat(service.higher(trump, suit, stronger, weaker))
                .as("%s should beat %s (%s trump, %s led)", stronger, weaker, trump, suit)
                .isEqualTo(stronger);

            // Order must not depend on which of the two was played first.
            assertThat(service.higher(trump, suit, weaker, stronger))
                .as("%s should still beat %s when played second", stronger, weaker)
                .isEqualTo(stronger);
        }
    }

    /**
     * The order dictated by the players, for a suit whose Ace is an ordinary card.
     *
     * Not trump:  Rey, Caballo, Sota, <b>Rovell</b>, 2, 3, 4, 5, 6, 7
     * Trump:      7 (manilla), <b>Rovell</b>, Rey, Caballo, Sota, 2, 3, 4, 5, 6
     *
     * So the Ace changes place: below the Sota in a plain suit, above the King in trump.
     * That asymmetry is the whole of D-6, and it is real.
     */
    static Stream<Arguments> playerConfirmedColumns() {
        return Stream.of(
            // "SERÍA: Rey de oros, Caballo, Sota, Rovell, 2, 3, 4, 5, 6, 7 de oros"
            column(BASTOS, OROS, List.of(
                card(OROS, REY), card(OROS, CABALLO), card(OROS, SOTA), card(OROS, AS),
                card(OROS, DOS), card(OROS, TRES), card(OROS, CUATRO),
                card(OROS, CINCO), card(OROS, SEIS), card(OROS, SIETE))),

            // "y cuando es triunfo: 7 de oros, Rovell, Rey, Caballo, Sota, 2, 3, 4, 5, 6"
            // (the Espadilla and Basto sit above, being trumps from other suits)
            column(OROS, OROS, List.of(
                card(OROS, SIETE), card(OROS, AS), card(OROS, REY),
                card(OROS, CABALLO), card(OROS, SOTA),
                card(OROS, DOS), card(OROS, TRES), card(OROS, CUATRO),
                card(OROS, CINCO), card(OROS, SEIS))),

            // "this goes for all the colors" - Copas has an ordinary Ace too, the Carabassa
            column(BASTOS, COPAS, List.of(
                card(COPAS, REY), card(COPAS, CABALLO), card(COPAS, SOTA), card(COPAS, AS),
                card(COPAS, DOS), card(COPAS, TRES), card(COPAS, CUATRO),
                card(COPAS, CINCO), card(COPAS, SEIS), card(COPAS, SIETE))),
            column(COPAS, COPAS, List.of(
                card(COPAS, SIETE), card(COPAS, AS), card(COPAS, REY),
                card(COPAS, CABALLO), card(COPAS, SOTA),
                card(COPAS, DOS), card(COPAS, TRES), card(COPAS, CUATRO),
                card(COPAS, CINCO), card(COPAS, SEIS))),

            // "but with different ordering for bastos and espadas" - no Ace of their own,
            // and the low cards run the other way
            column(OROS, ESPADAS, List.of(
                card(ESPADAS, REY), card(ESPADAS, CABALLO), card(ESPADAS, SOTA),
                card(ESPADAS, SIETE), card(ESPADAS, SEIS), card(ESPADAS, CINCO),
                card(ESPADAS, CUATRO), card(ESPADAS, TRES), card(ESPADAS, DOS))),
            column(ESPADAS, ESPADAS, List.of(
                card(ESPADAS, DOS), card(ESPADAS, REY), card(ESPADAS, CABALLO),
                card(ESPADAS, SOTA), card(ESPADAS, SIETE), card(ESPADAS, SEIS),
                card(ESPADAS, CINCO), card(ESPADAS, CUATRO), card(ESPADAS, TRES))),
            column(OROS, BASTOS, List.of(
                card(BASTOS, REY), card(BASTOS, CABALLO), card(BASTOS, SOTA),
                card(BASTOS, SIETE), card(BASTOS, SEIS), card(BASTOS, CINCO),
                card(BASTOS, CUATRO), card(BASTOS, TRES), card(BASTOS, DOS))),
            column(BASTOS, BASTOS, List.of(
                card(BASTOS, DOS), card(BASTOS, REY), card(BASTOS, CABALLO),
                card(BASTOS, SOTA), card(BASTOS, SIETE), card(BASTOS, SEIS),
                card(BASTOS, CINCO), card(BASTOS, CUATRO), card(BASTOS, TRES)))
        );
    }

    @ParameterizedTest(name = "{1} when {0} is trump, as the players stated it")
    @MethodSource("playerConfirmedColumns")
    void shouldRankEveryColumnAsThePlayersStatedIt(Suit trump, Suit suit, List<Card> strongestFirst) {
        shouldRankEveryColumnAsTheSpecTablesDo(trump, suit, strongestFirst);
    }

    /** Copas and Oros keep their Ace (Rovell) and run 2 down to 7. */
    private static List<Card> nonTrumpWithAce(Suit suit) {
        return List.of(
            card(suit, REY), card(suit, CABALLO), card(suit, SOTA), card(suit, AS),
            card(suit, DOS), card(suit, TRES), card(suit, CUATRO),
            card(suit, CINCO), card(suit, SEIS), card(suit, SIETE));
    }

    /** Espadas and Bastos have no Ace outside the specials, and run 7 down to 2. */
    private static List<Card> nonTrumpWithoutAce(Suit suit) {
        return List.of(
            card(suit, REY), card(suit, CABALLO), card(suit, SOTA),
            card(suit, SIETE), card(suit, SEIS), card(suit, CINCO),
            card(suit, CUATRO), card(suit, TRES), card(suit, DOS));
    }

    private static Arguments column(Suit trump, Suit suit, List<Card> strongestFirst) {
        return Arguments.of(trump, suit, strongestFirst);
    }

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }
}
