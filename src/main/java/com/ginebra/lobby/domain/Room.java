package com.ginebra.lobby.domain;

import com.ginebra.identity.domain.PlayerId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Room {

    public static final int MAX_PLAYERS = 5;

    private final RoomId id;
    private final List<RoomPlayer> players;
    private final Instant createdAt;
    private RoomStatus status;
    private GameId gameId; // nullable until game starts

    // Private constructor - use factory methods
    private Room(RoomId id, Instant createdAt, RoomStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.players = new ArrayList<>();
        this.gameId = null;
    }

    // Factory method for creating new room
    public static Room create(RoomId id, Instant createdAt) {
        return new Room(id, createdAt, RoomStatus.WAITING);
    }

    // Business logic: Add player
    public AddPlayerResult addPlayer(PlayerId playerId, String displayName, Instant joinedAt) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");

        // Validation: Room must be WAITING
        if (status != RoomStatus.WAITING) {
            return new AddPlayerResult.RoomNotWaiting(status);
        }

        // Validation: Not already full
        if (players.size() >= MAX_PLAYERS) {
            return new AddPlayerResult.RoomFull();
        }

        // Validation: Player not already in room
        if (hasPlayer(playerId)) {
            return new AddPlayerResult.PlayerAlreadyInRoom();
        }

        // Add player
        final var roomPlayer = new RoomPlayer(playerId, displayName, joinedAt);
        players.add(roomPlayer);

        // Check if room is now full
        if (players.size() == MAX_PLAYERS) {
            status = RoomStatus.STARTING;
            return new AddPlayerResult.RoomFull_GameShouldStart();
        }

        return new AddPlayerResult.Success();
    }

    // Business logic: Remove player
    public RemovePlayerResult removePlayer(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        // Validation: Can only remove if WAITING
        if (status != RoomStatus.WAITING) {
            return new RemovePlayerResult.CannotLeaveAfterStarting();
        }

        // Validation: Player must be in room
        if (!hasPlayer(playerId)) {
            return new RemovePlayerResult.PlayerNotInRoom();
        }

        // Remove player
        players.removeIf(p -> p.playerId().equals(playerId));

        // Check if room is now empty
        if (players.isEmpty()) {
            status = RoomStatus.ABANDONED;
            return new RemovePlayerResult.RoomNowEmpty_ShouldDelete();
        }

        return new RemovePlayerResult.Success();
    }

    // Business logic: Mark as converted to game
    public void convertToGame(GameId gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");

        if (status != RoomStatus.STARTING) {
            throw new IllegalStateException("Can only convert STARTING room to game, current status: " + status);
        }
        if (players.size() != MAX_PLAYERS) {
            throw new IllegalStateException("Can only convert room with exactly 5 players, current: " + players.size());
        }

        this.gameId = gameId;
        this.status = RoomStatus.CONVERTED;
    }

    /**
     * When something last happened here: it was made, or somebody joined it.
     *
     * Leaving does not count, because the only room a leave can leave behind is one with
     * people still in it, and their joining is already the later moment.
     */
    public Instant lastActivityAt() {
        return players.stream()
            .map(RoomPlayer::joinedAt)
            .max(Instant::compareTo)
            .filter(joined -> joined.isAfter(createdAt))
            .orElse(createdAt);
    }

    /**
     * Nobody has come near this room for long enough that nobody is going to.
     *
     * A room that became a game is never abandoned, whatever its age - it is the game's
     * address now, and the players look it up to find their table.
     */
    public boolean isAbandoned(Duration idleFor, Instant now) {
        Objects.requireNonNull(idleFor, "idleFor must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (status == RoomStatus.CONVERTED) {
            return false;
        }
        return !lastActivityAt().plus(idleFor).isAfter(now);
    }

    // Query methods
    public boolean hasPlayer(PlayerId playerId) {
        return players.stream().anyMatch(p -> p.playerId().equals(playerId));
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean canJoin() {
        return status == RoomStatus.WAITING && !isFull();
    }

    public boolean canLeave() {
        return status == RoomStatus.WAITING;
    }

    // Getters with defensive copies
    public RoomId id() {
        return id;
    }

    public List<RoomPlayer> players() {
        return List.copyOf(players); // Defensive copy
    }

    public Instant createdAt() {
        return createdAt;
    }

    public RoomStatus status() {
        return status;
    }

    public Optional<GameId> gameId() {
        return Optional.ofNullable(gameId);
    }
}
