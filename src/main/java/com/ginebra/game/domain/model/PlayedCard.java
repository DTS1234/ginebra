package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a card played by a specific player at a specific time.
 * Immutable value object tracking who played what and when.
 */
public record PlayedCard(
    PlayerId playerId,
    Card card,
    Instant playedAt
) {

    public PlayedCard {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(playedAt, "playedAt must not be null");
    }

    /**
     * Factory method to create a PlayedCard.
     */
    public static PlayedCard of(PlayerId playerId, Card card, Instant playedAt) {
        return new PlayedCard(playerId, card, playedAt);
    }
}
