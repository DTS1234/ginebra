package com.ginebra.game.domain.service;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.RoundResult;
import com.ginebra.game.domain.model.Settlement;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.domain.PlayerId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Prices a completed round against the posso (`payment-rules.md`, the players 2026-08-27).
 *
 * A hand is a <b>stake</b> the going side wins or loses, plus a few things paid whatever
 * happens. The stake is one <b>base</b> plus <b>+1 increments</b>:
 *
 * <pre>
 *   base       helped (a king was put on you)         2 each
 *              carried on after your own king fell    4
 *              you went alone                         5
 *
 *   +1         primeres   the first four basas in a row
 *   +1         estutxe    espadilla, manilla and basto, held between the side
 * </pre>
 *
 * Both increments cut both ways - <i>"cobran 1 más si hacen 5 o pagan 1 más si no llegan a
 * hacer 5"</i>. Treating the estutxe as a fee collected for going, which is what this did
 * before, left the book's <i>"Si perden i tenen l'estutxe, 3 cadegú"</i> paying 1; as a
 * stake it comes out at exactly 3.
 *
 * <p>The estutxe belongs to the <b>side, not a player</b>: one partner's espadilla and
 * basto with the other's manilla is an estutxe just the same. The opponents' own primeres
 * are worth nothing to them.
 *
 * <p>Outside the stake, never negated by the outcome:
 *
 * <ul>
 *   <li><b>Dengue, +1 to whoever was dealt it</b>, win or lose and on either side -
 *       <i>"siempre cobra una"</i>. Personal to one hand, unlike the estutxe.</li>
 *   <li><b>Todo, +1 to every player on the going side if made, -1 if called and missed.</b></li>
 *   <li><b>Four kings, +4</b> to whoever was dealt them, whatever they then decide.</li>
 *   <li><b>Stopping when your own king falls, -1</b> to the one who goes, and nobody else
 *       pays or collects.</li>
 * </ul>
 *
 * <p>Each opponent collects a flat <b>1</b> whenever the going side fails to make five,
 * and pays nothing when it succeeds - the pot covers the difference. The book's extra coin
 * for holding the going side under four basas is not applied: see `payment-rules.md` §6.
 *
 * <p>That the estutxe is a stake is also what keeps the source's <i>"Per guanyar i tindre
 * l'estutxe, 3 cadegú <b>(si n té el dengue, 4)</b>"</i> read as written. A side can hold
 * the estutxe between them with <b>nobody</b> holding the dengue - one partner takes the
 * espadilla and manilla, the other the basto - so the dengue really is a separate
 * condition worth a parenthetical, rather than something the estutxe always implies.
 */
public class SettlementCalculator {

    public static final int BASE_HELPED = 2;
    public static final int BASE_SELF_KING = 4;
    public static final int BASE_SOLEDAD = 5;
    public static final int FOUR_KINGS_AWARD = 4;

    /** Every named extra is worth the same one coin. */
    public static final int INCREMENT = 1;

    /** What each opponent collects when the going side falls short of five. */
    public static final int OPPOSING_SIDE_AWARD = 1;

    /** What it costs the one who goes to stop the hand when their own king falls. */
    public static final int STOPPING_COST = 1;

    /**
     * Prices a completed round.
     *
     * @param round the completed round
     * @param dealSnapshot every player's hand as it was dealt, before any card was played
     * @return the coins each player collects (positive) or pays (negative)
     */
    public Settlement settle(Round round, Map<PlayerId, List<Card>> dealSnapshot) {
        Objects.requireNonNull(round, "round must not be null");
        Objects.requireNonNull(dealSnapshot, "dealSnapshot must not be null");

        if (!round.isComplete()) {
            throw new IllegalStateException("Cannot settle a round that is not complete");
        }

        final var result = round.result().orElseThrow(
            () -> new IllegalStateException("Completed round has no result")
        );

        final var deltas = new HashMap<PlayerId, Integer>();
        for (final var player : round.playerOrder()) {
            deltas.put(player, 0);
        }

        if (result instanceof RoundResult.GoingSideWon won) {
            for (final var player : won.goingSide()) {
                deltas.merge(player, stakeOf(round, dealSnapshot), Integer::sum);
            }
        } else if (result instanceof RoundResult.GoingSideFailed failed) {
            for (final var player : failed.goingSide()) {
                deltas.merge(player, -stakeOf(round, dealSnapshot), Integer::sum);
            }
            for (final var player : failed.opposingSide()) {
                deltas.merge(player, OPPOSING_SIDE_AWARD, Integer::sum);
            }
        }

        // Stopping costs the one who goes 1, and that is the whole settlement: the hand
        // did not happen, so nothing else in it is worth anything - not even a dengue.
        if (result instanceof RoundResult.KingFell stopped) {
            deltas.merge(stopped.playerWhoGoes(), -STOPPING_COST, Integer::sum);
            return new Settlement(deltas);
        }
        // RoundResult.FourKings settles on the four-kings award alone.

        // The todo stands apart from the stake: it is its own bet.
        for (final var player : round.goingSide()) {
            deltas.merge(player, goingSideExtras(round), Integer::sum);
        }

        // "Per tindre es quatre reis, 4" - unconditional, like the dengue. A holder who
        // goes alone keeps it on top of whatever the hand then settles at.
        round.fourKingHolder().ifPresent(
            player -> deltas.merge(player, FOUR_KINGS_AWARD, Integer::sum)
        );

        // "El dengue sempre es cobra" - whoever was dealt it collects, whatever happened.
        for (final var player : round.playerOrder()) {
            if (hasDengue(dealSnapshot.get(player))) {
                deltas.merge(player, INCREMENT, Integer::sum);
            }
        }

        return new Settlement(deltas);
    }

    /**
     * What one player of the going side collects on a win, or pays on a loss: the base for
     * how they came to be going, and a coin each for primeres and for the estutxe.
     */
    private int stakeOf(Round round, Map<PlayerId, List<Card>> dealSnapshot) {
        var stake = baseOf(round);

        if (round.madePrimeres()) {
            stake += INCREMENT;
        }

        final var trump = round.trumpSuit().orElse(null);
        if (trump != null && hasEstutxe(cardsDealtTo(round.goingSide(), dealSnapshot), trump)) {
            stake += INCREMENT;
        }

        return stake;
    }

    /**
     * The todo, settled on its own: made is worth a coin to each of the going side, called
     * and missed costs each of them one, whatever the hand then did.
     */
    private int goingSideExtras(Round round) {
        if (round.madeTodo()) {
            return INCREMENT;
        }
        return round.todoCalled() ? -INCREMENT : 0;
    }

    /** Everything the given players were dealt, pooled. */
    private static List<Card> cardsDealtTo(
        Set<PlayerId> players,
        Map<PlayerId, List<Card>> dealSnapshot
    ) {
        return players.stream()
            .map(dealSnapshot::get)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .toList();
    }

    private int baseOf(Round round) {
        final var mode = round.mode().orElseThrow(
            () -> new IllegalStateException("Completed round with a side but no mode")
        );
        return switch (mode) {
            case HELPED -> BASE_HELPED;
            case SELF_KING -> BASE_SELF_KING;   // includes carrying on after it fell
            case SOLEDAD -> BASE_SOLEDAD;
            case FOUR_KINGS -> 0;  // awarded separately, see settle()
            case KING_FELL -> 0;
        };
    }

    /**
     * Espadilla and Basto in <b>one</b> hand. The dengue is personal - it is never made up
     * between partners.
     */
    public static boolean hasDengue(List<Card> hand) {
        if (hand == null) {
            return false;
        }
        return hand.stream().anyMatch(Card::isEspadilla)
            && hand.stream().anyMatch(Card::isBasto);
    }

    /**
     * Espadilla, Manilla and Basto <b>among the given cards</b>.
     *
     * Pass one hand to ask about a player, or a side's hands pooled to ask about the side -
     * which is how it is actually scored, because an estutxe may be made up between
     * partners: one holds the dengue, the other the manilla.
     */
    public static boolean hasEstutxe(List<Card> cards, Suit trumpSuit) {
        if (cards == null) {
            return false;
        }
        return cards.stream().anyMatch(Card::isEspadilla)
            && cards.stream().anyMatch(Card::isBasto)
            && cards.stream().anyMatch(c -> c.isManilla(trumpSuit));
    }
}
