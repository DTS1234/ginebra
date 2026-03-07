package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate root for the Ginebra card game.
 * Manages rounds, coin balances, and the overall game lifecycle.
 *
 * This class is immutable - mutation methods return new instances.
 */
public final class Game {

    public static final int PLAYER_COUNT = 5;
    public static final int INITIAL_COINS = 20;
    public static final int WIN_COINS = 2;
    public static final int LOSE_COINS = -2;
    public static final int DRAW_COINS = 1;
    public static final int DUENDE_BONUS = 1;
    public static final int ESTUCHE_BONUS = 2;
    public static final Duration SOLEDAD_TIMEOUT = Duration.ofMinutes(2);

    private final GameId gameId;
    private final List<PlayerId> players;
    private final List<Round> completedRounds;
    private final Round currentRound;
    private final Map<PlayerId, Integer> coinBalances;
    private final GameStatus status;
    private final Map<PlayerId, List<Card>> currentDealSnapshot;

    private Game(
        GameId gameId,
        List<PlayerId> players,
        List<Round> completedRounds,
        Round currentRound,
        Map<PlayerId, Integer> coinBalances,
        GameStatus status,
        Map<PlayerId, List<Card>> currentDealSnapshot
    ) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(completedRounds, "completedRounds must not be null");
        Objects.requireNonNull(coinBalances, "coinBalances must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (players.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "players must have " + PLAYER_COUNT + " entries, got: " + players.size()
            );
        }

        if (coinBalances.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "coinBalances must have " + PLAYER_COUNT + " entries, got: " + coinBalances.size()
            );
        }

        this.gameId = gameId;
        this.players = List.copyOf(players);
        this.completedRounds = List.copyOf(completedRounds);
        this.currentRound = currentRound;
        this.coinBalances = Map.copyOf(coinBalances);
        this.status = status;
        this.currentDealSnapshot = currentDealSnapshot != null ? deepCopyHands(currentDealSnapshot) : null;
    }

    // === Factory Method ===

    /**
     * Creates a new game, deals cards, and starts the first round in WAITING_FOR_SOLEDAD.
     *
     * @param gameId the game identifier
     * @param players the 5 players in clockwise order
     * @param random the random source for deck shuffling
     * @param now the current instant for computing soledad deadline
     * @return a new Game in IN_PROGRESS status with the first round started
     */
    public static Game start(GameId gameId, List<PlayerId> players, Random random, Instant now) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(players, "players must not be null");
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (players.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "Game requires exactly " + PLAYER_COUNT + " players, got: " + players.size()
            );
        }

        final var uniquePlayers = new HashSet<>(players);
        if (uniquePlayers.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("Duplicate players found");
        }

        final var initialCoins = new HashMap<PlayerId, Integer>();
        for (final var player : players) {
            initialCoins.put(player, INITIAL_COINS);
        }

        final var hands = dealCards(players, random);
        final var dealt = players.stream().map(hands::get).toList();
        final var espadillaIndex = Deck.findEspadillaHolder(dealt);
        final var playerWhoGoes = players.get(espadillaIndex);

        final var soledadDeadline = now.plus(SOLEDAD_TIMEOUT);
        final var round = Round.start(1, playerWhoGoes, players, hands, soledadDeadline);

        return new Game(
            gameId,
            players,
            List.of(),
            round,
            initialCoins,
            GameStatus.IN_PROGRESS,
            hands
        );
    }

    // === Query Methods ===

    public GameId gameId() {
        return gameId;
    }

    public List<PlayerId> players() {
        return players;
    }

    public List<Round> completedRounds() {
        return completedRounds;
    }

    public Optional<Round> currentRound() {
        return Optional.ofNullable(currentRound);
    }

    public Map<PlayerId, Integer> coinBalances() {
        return coinBalances;
    }

    public GameStatus status() {
        return status;
    }

    public boolean isInProgress() {
        return status == GameStatus.IN_PROGRESS;
    }

    public boolean isEnded() {
        return status == GameStatus.ENDED;
    }

    public int getCoins(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        final var coins = coinBalances.get(playerId);
        if (coins == null) {
            throw new IllegalArgumentException("Player not in game: " + playerId);
        }
        return coins;
    }

    public int roundNumber() {
        if (currentRound != null) {
            return currentRound.roundNumber();
        }
        return completedRounds.size();
    }

    public boolean isRoundComplete() {
        return currentRound != null && currentRound.isComplete();
    }

    // === Mutation Methods (return new instances) ===

    /**
     * Records a player passing Soledad in the current round.
     */
    public Game passSoledad(PlayerId playerId) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withSoledadPass(playerId);
        return withCurrentRound(updatedRound);
    }

    /**
     * Records a player declaring Soledad in the current round.
     */
    public Game declareSoledad(PlayerId playerId) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withSoledadDeclared(playerId);
        return withCurrentRound(updatedRound);
    }

    /**
     * Selects the trump suit for the current round.
     */
    public Game selectTrump(Suit trump) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withTrump(trump);
        return withCurrentRound(updatedRound);
    }

    /**
     * Plays a card in the current round.
     */
    public Game playCard(PlayerId playerId, Card card, Instant playedAt) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withCardPlayed(playerId, card, playedAt);
        return withCurrentRound(updatedRound);
    }

    /**
     * Completes the current basa with the determined winner.
     * If this causes the round to end, calculates coin changes and checks for bankruptcy.
     */
    public Game completeBasa(PlayerId winnerId) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.completeBasa(winnerId);

        if (!updatedRound.isComplete()) {
            return withCurrentRound(updatedRound);
        }

        // Round is complete — calculate coins
        final var coinChanges = calculateCoinChanges(updatedRound, currentDealSnapshot);
        final var newBalances = applyCoins(coinBalances, coinChanges);

        if (isAnyPlayerBankrupt(newBalances)) {
            return new Game(
                gameId,
                players,
                completedRounds,
                updatedRound,
                newBalances,
                GameStatus.ENDED,
                currentDealSnapshot
            );
        }

        return new Game(
            gameId,
            players,
            completedRounds,
            updatedRound,
            newBalances,
            GameStatus.IN_PROGRESS,
            currentDealSnapshot
        );
    }

    /**
     * Sets the teams for the current round when the first King is played.
     */
    public Game setTeams(Teams teams) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withTeams(teams);
        return withCurrentRound(updatedRound);
    }

    /**
     * Starts the next round after the current one has completed.
     *
     * @param random the random source for deck shuffling
     * @param now the current instant for computing soledad deadline
     * @return new Game with a fresh round started
     */
    public Game startNextRound(Random random, Instant now) {
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(now, "now must not be null");
        requireInProgress();
        requireCurrentRound();

        if (!currentRound.isComplete()) {
            throw new IllegalStateException("Cannot start next round: current round is not complete");
        }

        final var newCompletedRounds = new ArrayList<>(completedRounds);
        newCompletedRounds.add(currentRound);

        final var nextRoundNumber = newCompletedRounds.size() + 1;
        final var nextStarter = getPlayerToRight(currentRound.playerWhoGoes());

        final var hands = dealCards(players, random);
        final var soledadDeadline = now.plus(SOLEDAD_TIMEOUT);
        final var nextRound = Round.start(nextRoundNumber, nextStarter, players, hands, soledadDeadline);

        return new Game(
            gameId,
            players,
            newCompletedRounds,
            nextRound,
            coinBalances,
            GameStatus.IN_PROGRESS,
            hands
        );
    }

    // === Private Helpers ===

    private void requireInProgress() {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress: " + status);
        }
    }

    private void requireCurrentRound() {
        if (currentRound == null) {
            throw new IllegalStateException("No current round");
        }
    }

    private Game withCurrentRound(Round updatedRound) {
        return new Game(
            gameId,
            players,
            completedRounds,
            updatedRound,
            coinBalances,
            status,
            currentDealSnapshot
        );
    }

    private PlayerId getPlayerToRight(PlayerId player) {
        final var index = players.indexOf(player);
        final var rightIndex = (index + 1) % PLAYER_COUNT;
        return players.get(rightIndex);
    }

    /**
     * Calculates coin changes (deltas) for a completed round.
     * Returns a map of playerId -> delta (positive for winners, negative for losers).
     */
    Map<PlayerId, Integer> calculateCoinChanges(
        Round completedRound,
        Map<PlayerId, List<Card>> dealSnapshot
    ) {
        final var result = completedRound.result().orElseThrow(
            () -> new IllegalStateException("Round has no result")
        );
        final var changes = new HashMap<PlayerId, Integer>();

        if (result instanceof RoundResult.Draw) {
            for (final var player : players) {
                changes.put(player, DRAW_COINS);
            }
        } else if (result instanceof RoundResult.Win win) {
            final var winners = win.winners();
            final var trumpSuit = completedRound.trumpSuit().orElseThrow(
                () -> new IllegalStateException("Completed round has no trump suit")
            );
            final var bonus = calculateBonus(winners, trumpSuit, dealSnapshot);

            for (final var player : players) {
                if (winners.contains(player)) {
                    changes.put(player, WIN_COINS + bonus);
                } else {
                    changes.put(player, LOSE_COINS);
                }
            }
        }

        return Map.copyOf(changes);
    }

    private int calculateBonus(
        Set<PlayerId> winners,
        Suit trumpSuit,
        Map<PlayerId, List<Card>> dealSnapshot
    ) {
        var maxBonus = 0;
        for (final var winner : winners) {
            final var hand = dealSnapshot.get(winner);
            if (hand == null) {
                continue;
            }
            if (hasEstuche(hand, trumpSuit)) {
                maxBonus = Math.max(maxBonus, ESTUCHE_BONUS);
            } else if (hasDuende(hand)) {
                maxBonus = Math.max(maxBonus, DUENDE_BONUS);
            }
        }
        return maxBonus;
    }

    private static boolean hasDuende(List<Card> hand) {
        return hand.stream().anyMatch(Card::isEspadilla)
            && hand.stream().anyMatch(Card::isBasto);
    }

    private static boolean hasEstuche(List<Card> hand, Suit trumpSuit) {
        return hasDuende(hand)
            && hand.stream().anyMatch(c -> c.isManilla(trumpSuit));
    }

    static Map<PlayerId, Integer> applyCoins(
        Map<PlayerId, Integer> currentBalances,
        Map<PlayerId, Integer> changes
    ) {
        final var newBalances = new HashMap<PlayerId, Integer>();
        for (final var entry : currentBalances.entrySet()) {
            final var change = changes.getOrDefault(entry.getKey(), 0);
            newBalances.put(entry.getKey(), entry.getValue() + change);
        }
        return Map.copyOf(newBalances);
    }

    private static boolean isAnyPlayerBankrupt(Map<PlayerId, Integer> balances) {
        return balances.values().stream().anyMatch(coins -> coins <= 0);
    }

    private static Map<PlayerId, List<Card>> dealCards(List<PlayerId> players, Random random) {
        final var deck = Deck.create().shuffle(random);
        final var dealt = deck.deal(PLAYER_COUNT);
        final var hands = new HashMap<PlayerId, List<Card>>();
        for (var i = 0; i < players.size(); i++) {
            hands.put(players.get(i), dealt.get(i));
        }
        return hands;
    }

    private static Map<PlayerId, List<Card>> deepCopyHands(Map<PlayerId, List<Card>> original) {
        final var copy = new HashMap<PlayerId, List<Card>>();
        for (final var entry : original.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
