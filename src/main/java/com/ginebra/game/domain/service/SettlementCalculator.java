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
 *   +1         primeres   the first four basas in a row, by the going side
 * </pre>
 *
 * That figure is collected on a win and paid on a loss, so making primeres raises the
 * going side's stake in both directions - and the opponents' own primeres are worth
 * nothing to them (confirmed by the players 2026-08-26).
 *
 * Four items sit outside the stake and are only ever collected or charged, never negated
 * by the outcome:
 *
 * <ul>
 *   <li><b>Estutxe, +1 to every player on the going side.</b> <i>"Si tens l'estutxe i
 *       vas"</i> - it pays because you went, not because you won, and it pays the whole
 *       side rather than just the holder. That is what makes the source's <i>"Per guanyar
 *       i tindre l'estutxe, 3 cadegú (si n té el dengue, 4)"</i> come out exactly: 2 base
 *       + 1 estutxe each, and the one who also holds the dengue takes 4.</li>
 *   <li><b>Dengue, +1 to whoever was dealt it</b>, win or lose and on either side -
 *       <i>"El dengue sempre es cobra"</i>. Personal to the holder, unlike the estutxe.</li>
 *   <li><b>Todo, +1 to every player on the going side if made, -1 if called and missed.</b></li>
 *   <li><b>Four kings, +4</b> to whoever was dealt them, whatever they then decide, and a
 *       <b>forced king costs its owner 1</b>.</li>
 * </ul>
 *
 * The opposing side collects a flat 1 when the going side fails, or 2 if the going side
 * was held under four basas. It pays nothing when the going side wins: the source lists no
 * charge for losing defenders, and the pot covers the difference.
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
                deltas.merge(player, stakeOf(round), Integer::sum);
            }
        } else if (result instanceof RoundResult.GoingSideFailed failed) {
            for (final var player : failed.goingSide()) {
                deltas.merge(player, -stakeOf(round), Integer::sum);
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

        // Items the going side collects for what it held or did, not for winning.
        for (final var player : round.goingSide()) {
            deltas.merge(player, goingSideExtras(round, dealSnapshot), Integer::sum);
        }

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
    private int stakeOf(Round round) {
        return round.madePrimeres() ? baseOf(round) + INCREMENT : baseOf(round);
    }

    /**
     * What every player on the going side collects regardless of the outcome: the estutxe
     * because they went, and the todo they made - or owes, if they called it and missed.
     */
    private int goingSideExtras(Round round, Map<PlayerId, List<Card>> dealSnapshot) {
        var extras = 0;

        final var trump = round.trumpSuit().orElse(null);
        if (trump != null && anyOnGoingSideHasEstutxe(round, dealSnapshot, trump)) {
            extras += INCREMENT;
        }

        if (round.madeTodo()) {
            extras += INCREMENT;
        } else if (round.todoCalled()) {
            extras -= INCREMENT;
        }

        return extras;
    }

    private boolean anyOnGoingSideHasEstutxe(
        Round round,
        Map<PlayerId, List<Card>> dealSnapshot,
        Suit trump
    ) {
        return round.goingSide().stream()
            .anyMatch(player -> hasEstutxe(dealSnapshot.get(player), trump));
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
