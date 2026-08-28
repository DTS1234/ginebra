package com.ginebra.game.domain.model;

import com.ginebra.game.domain.service.SettlementCalculator;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate root for the Ginebra card game.
 * Manages rounds, the posso, coin balances and the overall game lifecycle.
 *
 * Money does not move between players. Every player antes an equal stake into the
 * <b>posso</b> in the middle of the table, and each round is settled by collecting from it
 * or paying into it (rules-source.md §3). The two sides of a settlement do not balance:
 * the pot absorbs the difference, and is topped up in equal parts when it runs dry.
 *
 * This class is immutable - mutation methods return new instances.
 */
public final class Game {

    public static final int PLAYER_COUNT = 5;

    /** What each player brings to the table. */
    public static final int INITIAL_COINS = 20;

    /** Each player's equal contribution to the posso, taken at the start and on a top-up. */
    public static final int ANTE = 5;

    public static final Duration SOLEDAD_TIMEOUT = Duration.ofMinutes(2);

    private static final SettlementCalculator SETTLEMENT = new SettlementCalculator();

    private final GameId gameId;
    private final List<PlayerId> players;
    private final List<Round> completedRounds;
    private final Round currentRound;
    private final Map<PlayerId, Integer> coinBalances;
    private final int posso;
    private final GameStatus status;
    private final Map<PlayerId, List<Card>> currentDealSnapshot;

    private Game(
        GameId gameId,
        List<PlayerId> players,
        List<Round> completedRounds,
        Round currentRound,
        Map<PlayerId, Integer> coinBalances,
        int posso,
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

        if (posso < 0) {
            throw new IllegalArgumentException("posso must not be negative, got: " + posso);
        }

        this.gameId = gameId;
        this.players = List.copyOf(players);
        this.completedRounds = List.copyOf(completedRounds);
        this.currentRound = currentRound;
        this.coinBalances = Map.copyOf(coinBalances);
        this.posso = posso;
        this.status = status;
        this.currentDealSnapshot = currentDealSnapshot != null ? deepCopyHands(currentDealSnapshot) : null;
    }

    // === Factory Method ===

    /**
     * Creates a new game, forms the posso, deals cards, and starts the first round.
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
            initialCoins.put(player, INITIAL_COINS - ANTE);
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
            ANTE * PLAYER_COUNT,
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

    /** What is currently in the middle of the table. */
    public int posso() {
        return posso;
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
        final var updated = withCurrentRound(updatedRound);

        // The four-king holder declining to play the hand out ends it there.
        return updatedRound.isComplete() ? updated.settleCurrentRound() : updated;
    }

    /**
     * Records a player declaring Soledad in the current round.
     */
    public Game declareSoledad(PlayerId playerId) {
        requireInProgress();
        requireCurrentRound();

        return withCurrentRound(currentRound.withSoledadDeclared(playerId));
    }

    /**
     * Records the one who goes calling "es primer rei aida".
     */
    public Game callFirstKing() {
        requireInProgress();
        requireCurrentRound();

        return withCurrentRound(currentRound.withFirstKingCalled());
    }

    /**
     * Records the going side's "fer todo" decision. Declining ends and settles the round.
     *
     * @param call true to play on for all eight basas, false to bank the win
     */
    /**
     * The one who goes answering for their own forced king: carry on alone, or stop.
     */
    public Game decideKingChoice(PlayerId playerId, boolean carryOn) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withKingChoice(playerId, carryOn);
        final var updated = withCurrentRound(updatedRound);

        return updatedRound.isComplete() ? updated.settleCurrentRound() : updated;
    }

    public Game decideTodo(PlayerId playerId, boolean call) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = call
            ? currentRound.withTodoCalled(playerId)
            : currentRound.withTodoDeclined(playerId);
        final var updated = withCurrentRound(updatedRound);

        return updatedRound.isComplete() ? updated.settleCurrentRound() : updated;
    }

    /**
     * Selects the trump suit for the current round.
     */
    public Game selectTrump(Suit trump) {
        requireInProgress();
        requireCurrentRound();

        return withCurrentRound(currentRound.withTrump(trump));
    }

    /**
     * Plays a card in the current round.
     */
    public Game playCard(PlayerId playerId, Card card, Instant playedAt) {
        requireInProgress();
        requireCurrentRound();

        return withCurrentRound(currentRound.withCardPlayed(playerId, card, playedAt));
    }

    /**
     * Records the king that decides the round's shape, settling immediately if it ended the
     * hand - which it does when the mà's own king was forced out.
     *
     * @param playerId the player who played the king
     * @param forced whether they had no legal alternative
     */
    public Game resolveKing(PlayerId playerId, boolean forced) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.withKingPlayed(playerId, forced);
        final var updated = withCurrentRound(updatedRound);

        return updatedRound.isComplete() ? updated.settleCurrentRound() : updated;
    }

    /**
     * Completes the current basa with the determined winner, settling the round if that
     * decided it.
     */
    public Game completeBasa(PlayerId winnerId) {
        requireInProgress();
        requireCurrentRound();

        final var updatedRound = currentRound.completeBasa(winnerId);
        final var updated = withCurrentRound(updatedRound);

        return updatedRound.isComplete() ? updated.settleCurrentRound() : updated;
    }

    /**
     * Sets the teams for the current round when the first King is played.
     *
     * @deprecated use {@link #resolveKing(PlayerId, boolean)}, which also handles the two
     *             solo cases and the forced king.
     */
    @Deprecated
    public Game setTeams(Teams teams) {
        requireInProgress();
        requireCurrentRound();

        return withCurrentRound(currentRound.withTeams(teams));
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
            posso,
            GameStatus.IN_PROGRESS,
            hands
        );
    }

    // === Settlement ===

    /**
     * Prices the completed current round and moves the coins between the players and the
     * posso, topping the posso up in equal parts if it cannot cover the payout.
     *
     * The game ends when a player can no longer cover what they owe or what they are asked
     * to stake.
     */
    private Game settleCurrentRound() {
        final var settlement = SETTLEMENT.settle(currentRound, currentDealSnapshot);

        var balances = new HashMap<>(coinBalances);
        var pot = posso;

        // "Si el posso s'acaba... es torna a renovar o a afegir... a parts iguals."
        while (pot < settlement.totalCollected()) {
            if (players.stream().anyMatch(p -> balances.get(p) < ANTE)) {
                return ended(balances, pot);
            }
            for (final var player : players) {
                balances.merge(player, -ANTE, Integer::sum);
            }
            pot += ANTE * PLAYER_COUNT;
        }

        for (final var entry : settlement.playerDeltas().entrySet()) {
            balances.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        pot += settlement.possoDelta();

        if (balances.values().stream().anyMatch(coins -> coins < 0)) {
            return ended(balances, pot);
        }

        return new Game(
            gameId, players, completedRounds, currentRound,
            balances, pot, GameStatus.IN_PROGRESS, currentDealSnapshot
        );
    }

    /**
     * What one round moved, for reporting. Recomputed rather than stored, so it always
     * matches the settlement that was applied.
     */
    public Settlement settlementOf(Round completedRound, Map<PlayerId, List<Card>> dealSnapshot) {
        return SETTLEMENT.settle(completedRound, dealSnapshot);
    }

    public Map<PlayerId, List<Card>> currentDealSnapshot() {
        return currentDealSnapshot;
    }

    private Game ended(Map<PlayerId, Integer> balances, int pot) {
        return new Game(
            gameId, players, completedRounds, currentRound,
            balances, Math.max(pot, 0), GameStatus.ENDED, currentDealSnapshot
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
            posso,
            status,
            currentDealSnapshot
        );
    }

    private PlayerId getPlayerToRight(PlayerId player) {
        final var index = players.indexOf(player);
        final var rightIndex = (index + 1) % PLAYER_COUNT;
        return players.get(rightIndex);
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
