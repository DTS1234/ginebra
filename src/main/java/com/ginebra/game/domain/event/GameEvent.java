package com.ginebra.game.domain.event;

import com.ginebra.game.domain.model.*;
import com.ginebra.identity.domain.PlayerId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain events produced by the game engine.
 * Each event represents a significant state change in the game.
 */
public sealed interface GameEvent {

    record TrumpSelected(
        PlayerId byPlayer,
        Suit suit,
        PlayerId currentTurn
    ) implements GameEvent {}

    /**
     * @param basaNumber which basa the card went into. Without it a client cannot tell a
     *                   card that belongs on the table it is showing from one that belongs
     *                   to the next basa, and a missed or reordered BasaWon leaves it
     *                   holding a table that never existed.
     */
    record CardPlayed(
        PlayerId playerId,
        Card card,
        PlayerId nextTurn,
        int basaNumber
    ) implements GameEvent {}

    record BasaWon(
        int basaNumber,
        PlayerId winner,
        List<PlayedCard> cards,
        Map<PlayerId, Integer> basasWon,
        int nextBasaNumber,
        PlayerId nextStarter
    ) implements GameEvent {}

    /**
     * The king that decided the shape of the round has been played: who goes with whom, or
     * alone, and whether the king was forced out of its owner.
     */
    record SideDecided(
        RoundMode mode,
        Set<PlayerId> goingSide,
        Set<PlayerId> opposingSide,
        PlayerId byPlayer,
        Card king,
        boolean forced
    ) implements GameEvent {}

    /**
     * The going side decided whether to go for "fer todo".
     */
    record TodoDecided(
        PlayerId byPlayer,
        boolean called,
        PlayerId currentTurn
    ) implements GameEvent {}

    record RoundEnded(
        int roundNumber,
        RoundResult result,
        Map<PlayerId, Integer> coinChanges,
        Map<PlayerId, Integer> newBalances,
        int posso
    ) implements GameEvent {}

    record GameEnded(
        String reason,
        Map<PlayerId, Integer> finalBalances
    ) implements GameEvent {}

    record SoledadPassed(
        PlayerId playerId,
        List<PlayerId> remainingPlayers
    ) implements GameEvent {}

    record SoledadDeclared(PlayerId byPlayer) implements GameEvent {}

    record SoledadWindowClosed(
        boolean declared,
        PlayerId awaitingTrumpFrom
    ) implements GameEvent {}

    record PlayerConnected(PlayerId playerId) implements GameEvent {}

    record PlayerDisconnected(PlayerId playerId) implements GameEvent {}

    record GameError(String code, String message) implements GameEvent {}
}
