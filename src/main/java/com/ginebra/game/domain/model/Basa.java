package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single basa (trick) in a round.
 * A basa consists of exactly 5 cards played in order, one per player.
 * The winner is determined after all 5 cards are played.
 *
 * This class is immutable - mutation methods return new instances.
 */
public final class Basa {

    public static final int CARDS_PER_BASA = 5;
    public static final int MIN_BASA_NUMBER = 1;
    public static final int MAX_BASA_NUMBER = 8;

    private final int basaNumber;
    private final PlayerId startingPlayer;
    private final List<PlayedCard> cardsPlayed;
    private final PlayerId winner;
    private final BasaStatus status;

    private Basa(
        int basaNumber,
        PlayerId startingPlayer,
        List<PlayedCard> cardsPlayed,
        PlayerId winner,
        BasaStatus status
    ) {
        if (basaNumber < MIN_BASA_NUMBER || basaNumber > MAX_BASA_NUMBER) {
            throw new IllegalArgumentException(
                "basaNumber must be between " + MIN_BASA_NUMBER + " and " + MAX_BASA_NUMBER + ", got: " + basaNumber
            );
        }
        Objects.requireNonNull(startingPlayer, "startingPlayer must not be null");
        Objects.requireNonNull(cardsPlayed, "cardsPlayed must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (cardsPlayed.size() > CARDS_PER_BASA) {
            throw new IllegalArgumentException("cardsPlayed cannot exceed " + CARDS_PER_BASA);
        }

        if (status == BasaStatus.COMPLETE) {
            if (winner == null) {
                throw new IllegalArgumentException("COMPLETE basa must have a winner");
            }
            if (cardsPlayed.size() != CARDS_PER_BASA) {
                throw new IllegalArgumentException("COMPLETE basa must have exactly " + CARDS_PER_BASA + " cards");
            }
        }

        if (status == BasaStatus.IN_PROGRESS && winner != null) {
            throw new IllegalArgumentException("IN_PROGRESS basa cannot have a winner");
        }

        this.basaNumber = basaNumber;
        this.startingPlayer = startingPlayer;
        this.cardsPlayed = List.copyOf(cardsPlayed);
        this.winner = winner;
        this.status = status;
    }

    // === Factory Methods ===

    /**
     * Creates a new basa ready for play.
     *
     * @param basaNumber the basa number (1-8)
     * @param startingPlayer the player who plays first
     * @return a new Basa in IN_PROGRESS status
     */
    public static Basa start(int basaNumber, PlayerId startingPlayer) {
        return new Basa(
            basaNumber,
            startingPlayer,
            List.of(),
            null,
            BasaStatus.IN_PROGRESS
        );
    }

    // === Query Methods ===

    public int basaNumber() {
        return basaNumber;
    }

    public PlayerId startingPlayer() {
        return startingPlayer;
    }

    public List<PlayedCard> cardsPlayed() {
        return cardsPlayed;
    }

    public Optional<PlayerId> winner() {
        return Optional.ofNullable(winner);
    }

    public BasaStatus status() {
        return status;
    }

    /**
     * Returns the suit that was led (first card's suit), or empty if no cards played.
     */
    public Optional<Suit> ledSuit() {
        if (cardsPlayed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cardsPlayed.get(0).card().suit());
    }

    public boolean isComplete() {
        return status == BasaStatus.COMPLETE;
    }

    public boolean isInProgress() {
        return status == BasaStatus.IN_PROGRESS;
    }

    public int cardCount() {
        return cardsPlayed.size();
    }

    public boolean canAcceptCard() {
        return status == BasaStatus.IN_PROGRESS && cardsPlayed.size() < CARDS_PER_BASA;
    }

    /**
     * Returns the cards played as a list of Card (without player/time info).
     * Useful for passing to CardRankingService.
     */
    public List<Card> cards() {
        return cardsPlayed.stream()
            .map(PlayedCard::card)
            .toList();
    }

    /**
     * Finds the PlayedCard that matches the given card.
     * Useful for determining winner after CardRankingService.findWinner().
     *
     * @param card the card to find
     * @return the PlayedCard containing the card
     * @throws IllegalArgumentException if card not found in basa
     */
    public PlayedCard findPlayedCard(Card card) {
        Objects.requireNonNull(card, "card must not be null");

        return cardsPlayed.stream()
            .filter(pc -> pc.card().equals(card))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Card not found in basa: " + card));
    }

    // === Mutation Methods (return new instances) ===

    /**
     * Adds a played card to this basa and returns a new Basa instance.
     * Does not determine winner - use complete() after all 5 cards are played.
     *
     * @param playedCard the card being played
     * @return new Basa with the card added
     * @throws IllegalStateException if basa is complete or already has 5 cards
     */
    public Basa withCard(PlayedCard playedCard) {
        Objects.requireNonNull(playedCard, "playedCard must not be null");

        if (!canAcceptCard()) {
            throw new IllegalStateException(
                "Cannot add card: basa is " + status + " with " + cardsPlayed.size() + " cards"
            );
        }

        final var newCards = new ArrayList<>(cardsPlayed);
        newCards.add(playedCard);

        return new Basa(
            basaNumber,
            startingPlayer,
            newCards,
            null,
            BasaStatus.IN_PROGRESS
        );
    }

    /**
     * Completes this basa with the determined winner.
     * Should only be called when all 5 cards have been played.
     *
     * @param winnerId the player who won this basa
     * @return new Basa marked as complete with winner set
     * @throws IllegalStateException if not exactly 5 cards played or already complete
     */
    public Basa complete(PlayerId winnerId) {
        Objects.requireNonNull(winnerId, "winnerId must not be null");

        if (cardsPlayed.size() != CARDS_PER_BASA) {
            throw new IllegalStateException(
                "Cannot complete basa: need " + CARDS_PER_BASA + " cards, have " + cardsPlayed.size()
            );
        }

        if (status == BasaStatus.COMPLETE) {
            throw new IllegalStateException("Basa is already complete");
        }

        return new Basa(
            basaNumber,
            startingPlayer,
            cardsPlayed,
            winnerId,
            BasaStatus.COMPLETE
        );
    }
}
