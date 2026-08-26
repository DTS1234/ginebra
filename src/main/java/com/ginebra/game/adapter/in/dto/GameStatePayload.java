package com.ginebra.game.adapter.in.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Full game state DTO sent to a specific player on connect/reconnect.
 * Personalized: only the requesting player's hand is included.
 */
public record GameStatePayload(
    String gameId,
    String status,
    List<PlayerDto> players,
    Map<String, Integer> coinBalances,
    int posso,
    RoundPayload currentRound
) {
    public GameStatePayload {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(coinBalances, "coinBalances must not be null");
    }

    public record PlayerDto(String id, int seatPosition) {}

    public record RoundPayload(
        int roundNumber,
        String status,
        String trumpSuit,
        String playerWhoGoes,
        String trumpChooser,
        List<CardDto> yourHand,
        String currentTurn,
        Map<String, Integer> basasWon,
        BasaPayload currentBasa,
        TeamsPayload teams,
        String mode,
        List<String> ledSuits,
        List<String> goingSide,
        List<String> opposingSide,
        boolean firstKingCalled,
        String todoCaller,
        boolean todoCalled,
        List<String> soledadPasses,
        String soledadPlayer,
        String fourKingHolder,
        String soledadDeadline
    ) {}

    public record BasaPayload(
        int basaNumber,
        String startingPlayer,
        List<PlayedCardDto> cardsPlayed
    ) {}

    public record PlayedCardDto(String playerId, CardDto card) {}

    public record TeamsPayload(
        List<String> teamOfTwo,
        List<String> teamOfThree
    ) {}
}
