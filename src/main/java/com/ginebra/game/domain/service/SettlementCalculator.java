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

/**
 * Prices a completed round against the posso (rules-source.md §5 and §6).
 *
 * The published tables are one <b>base</b> plus a set of <b>+1 increments</b>:
 *
 * <pre>
 *   base       helped (a king was put on you)   2 each
 *              you put your own king            4
 *              you went alone                   5
 *              you were dealt the four kings    4
 *
 *   +1 each    primeres   the first four basas in a row
 *              todo       all eight basas - which contains primeres
 *              dengue     espadilla + basto
 *              estutxe    espadilla + manilla + basto, on top of the dengue
 * </pre>
 *
 * The same figure is collected on a win and paid on a loss, so holding the estutxe or
 * making primeres raises your stake in both directions. Three things sit outside the
 * ladder: the <b>dengue is always collected</b>, win or lose and on either side - <i>"El
 * dengue sempre es cobra"</i> - the <b>four kings are always worth 4</b> to whoever was
 * dealt them, whether they take the 4 and end the hand or go alone and play it out, and a
 * <b>forced king costs its owner 1</b>.
 *
 * The opposing side collects a flat 1 when the going side fails, or 2 if the going side
 * was held under four basas. It pays nothing when the going side wins: the source lists no
 * charge for losing defenders, and the pot covers the difference.
 *
 * <p>Two readings the source leaves open, resolved here and recorded in rules-diff.md:
 * the printed row <i>"Per guanyar i tindre l'estutxe, 3 cadegú (si n té el dengue, 4)"</i>
 * is taken at its parenthetical - the estutxe contains the dengue, so it always scores
 * both, for 4. And the source does not say whose primeres raises a losing side's payment;
 * here it is the going side's own, which is what makes <i>"Si perden i tenen l'estutxe"</i>
 * read consistently.
 */
public class SettlementCalculator {

    public static final int BASE_HELPED = 2;
    public static final int BASE_SELF_KING = 4;
    public static final int BASE_SOLEDAD = 5;
    public static final int FOUR_KINGS_AWARD = 4;

    /** Every named extra is worth the same one coin. */
    public static final int INCREMENT = 1;

    public static final int OPPOSING_SIDE_AWARD = 1;
    public static final int OPPOSING_SIDE_AWARD_HELD_LOW = 2;

    /** Holding the going side under this many basas doubles what the opponents collect. */
    public static final int HELD_LOW_THRESHOLD = 4;

    public static final int FORCED_KING_PENALTY = 1;

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
                deltas.merge(player, stakeOf(round, player, dealSnapshot), Integer::sum);
            }
        } else if (result instanceof RoundResult.GoingSideFailed failed) {
            for (final var player : failed.goingSide()) {
                deltas.merge(player, -stakeOf(round, player, dealSnapshot), Integer::sum);
            }
            final var award = failed.goingSideBasas() < HELD_LOW_THRESHOLD
                ? OPPOSING_SIDE_AWARD_HELD_LOW
                : OPPOSING_SIDE_AWARD;
            for (final var player : failed.opposingSide()) {
                deltas.merge(player, award, Integer::sum);
            }
        }
        // RoundResult.FourKings settles on the four-kings award alone, and
        // RoundResult.KingFell on the forced-king penalty alone.

        // "Per tindre es quatre reis, 4" - unconditional, like the dengue. A holder who
        // goes alone keeps it on top of whatever the hand then settles at.
        round.fourKingHolder().ifPresent(
            player -> deltas.merge(player, FOUR_KINGS_AWARD, Integer::sum)
        );

        round.forcedKingPlayer().ifPresent(
            player -> deltas.merge(player, -FORCED_KING_PENALTY, Integer::sum)
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
     * What one player of the going side collects on a win, or pays on a loss.
     */
    private int stakeOf(Round round, PlayerId player, Map<PlayerId, List<Card>> dealSnapshot) {
        var stake = baseOf(round);

        if (round.madePrimeres()) {
            stake += INCREMENT;
        }
        if (round.madeTodo()) {
            stake += INCREMENT;
        }
        final var trump = round.trumpSuit().orElse(null);
        if (trump != null && hasEstutxe(dealSnapshot.get(player), trump)) {
            stake += INCREMENT;
        }
        return stake;
    }

    private int baseOf(Round round) {
        final var mode = round.mode().orElseThrow(
            () -> new IllegalStateException("Completed round with a side but no mode")
        );
        return switch (mode) {
            case HELPED -> BASE_HELPED;
            case SELF_KING -> BASE_SELF_KING;
            case SOLEDAD -> BASE_SOLEDAD;
            case FOUR_KINGS -> 0;  // awarded separately, see settle()
            case KING_FELL -> 0;
        };
    }

    /** Espadilla and Basto in the same hand. */
    public static boolean hasDengue(List<Card> hand) {
        if (hand == null) {
            return false;
        }
        return hand.stream().anyMatch(Card::isEspadilla)
            && hand.stream().anyMatch(Card::isBasto);
    }

    /** Espadilla, Manilla and Basto in the same hand. */
    public static boolean hasEstutxe(List<Card> hand, Suit trumpSuit) {
        if (hand == null) {
            return false;
        }
        return hasDengue(hand) && hand.stream().anyMatch(c -> c.isManilla(trumpSuit));
    }
}
