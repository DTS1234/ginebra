package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a single round in the game.
 * A round consists of up to 8 basas (tricks) played until one team wins 5 or a draw occurs.
 *
 * This class is immutable - mutation methods return new instances.
 */
public final class Round {

    public static final int MAX_BASAS = 8;
    public static final int BASAS_TO_WIN = 5;
    public static final int PLAYER_COUNT = 5;
    public static final int CARDS_PER_HAND = 8;
    public static final int TOTAL_CARDS = 40;

    private final int roundNumber;
    private final Suit trumpSuit;
    private final PlayerId playerWhoGoes;
    private final List<PlayerId> playerOrder;
    private final List<Basa> completedBasas;
    private final Basa currentBasa;
    private final Map<PlayerId, List<Card>> hands;
    private final Teams teams;
    private final RoundStatus status;
    private final RoundResult result;
    private final Set<PlayerId> soledadPasses;
    private final PlayerId soledadPlayer;
    private final Instant soledadDeadline;

    private Round(
        int roundNumber,
        Suit trumpSuit,
        PlayerId playerWhoGoes,
        List<PlayerId> playerOrder,
        List<Basa> completedBasas,
        Basa currentBasa,
        Map<PlayerId, List<Card>> hands,
        Teams teams,
        RoundStatus status,
        RoundResult result,
        Set<PlayerId> soledadPasses,
        PlayerId soledadPlayer,
        Instant soledadDeadline
    ) {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1, got: " + roundNumber);
        }
        Objects.requireNonNull(playerWhoGoes, "playerWhoGoes must not be null");
        Objects.requireNonNull(playerOrder, "playerOrder must not be null");
        Objects.requireNonNull(completedBasas, "completedBasas must not be null");
        Objects.requireNonNull(hands, "hands must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(soledadPasses, "soledadPasses must not be null");

        if (playerOrder.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "playerOrder must have " + PLAYER_COUNT + " players, got: " + playerOrder.size()
            );
        }

        if (!playerOrder.contains(playerWhoGoes)) {
            throw new IllegalArgumentException("playerWhoGoes must be in playerOrder");
        }

        if (hands.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException(
                "hands must have " + PLAYER_COUNT + " entries, got: " + hands.size()
            );
        }

        for (final var player : playerOrder) {
            if (!hands.containsKey(player)) {
                throw new IllegalArgumentException("Missing hand for player: " + player);
            }
        }

        this.roundNumber = roundNumber;
        this.trumpSuit = trumpSuit;
        this.playerWhoGoes = playerWhoGoes;
        this.playerOrder = List.copyOf(playerOrder);
        this.completedBasas = List.copyOf(completedBasas);
        this.currentBasa = currentBasa;
        this.hands = deepCopyHands(hands);
        this.teams = teams;
        this.status = status;
        this.result = result;
        this.soledadPasses = Set.copyOf(soledadPasses);
        this.soledadPlayer = soledadPlayer;
        this.soledadDeadline = soledadDeadline;
    }

    // === Factory Methods ===

    /**
     * Creates a new round in the WAITING_FOR_SOLEDAD state.
     *
     * @param roundNumber the round number (1-based)
     * @param playerWhoGoes the player who will select trump and start first basa
     * @param playerOrder the 5 players in clockwise order
     * @param hands the cards dealt to each player (8 each)
     * @param soledadDeadline deadline for soledad declarations
     * @return a new Round in WAITING_FOR_SOLEDAD status
     */
    public static Round start(
        int roundNumber,
        PlayerId playerWhoGoes,
        List<PlayerId> playerOrder,
        Map<PlayerId, List<Card>> hands,
        Instant soledadDeadline
    ) {
        Objects.requireNonNull(hands, "hands must not be null");
        Objects.requireNonNull(soledadDeadline, "soledadDeadline must not be null");

        // Validate each hand has correct card count
        for (final var entry : hands.entrySet()) {
            if (entry.getValue().size() != CARDS_PER_HAND) {
                throw new IllegalArgumentException(
                    "Each hand must have " + CARDS_PER_HAND + " cards, player " +
                    entry.getKey() + " has " + entry.getValue().size()
                );
            }
        }

        // Validate no duplicate cards
        final var allCards = hands.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());

        if (allCards.size() != TOTAL_CARDS) {
            throw new IllegalArgumentException(
                "Total cards must be " + TOTAL_CARDS + ", got: " + allCards.size()
            );
        }

        final var uniqueCards = new HashSet<>(allCards);
        if (uniqueCards.size() != TOTAL_CARDS) {
            throw new IllegalArgumentException("Duplicate cards found in hands");
        }

        return new Round(
            roundNumber,
            null,
            playerWhoGoes,
            playerOrder,
            List.of(),
            null,
            hands,
            null,
            RoundStatus.WAITING_FOR_SOLEDAD,
            null,
            Set.of(),
            null,
            soledadDeadline
        );
    }

    // === Query Methods ===

    public int roundNumber() {
        return roundNumber;
    }

    public Optional<Suit> trumpSuit() {
        return Optional.ofNullable(trumpSuit);
    }

    public PlayerId playerWhoGoes() {
        return playerWhoGoes;
    }

    public List<PlayerId> playerOrder() {
        return playerOrder;
    }

    public List<Basa> completedBasas() {
        return completedBasas;
    }

    public Optional<Basa> currentBasa() {
        return Optional.ofNullable(currentBasa);
    }

    public List<Card> getHand(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        final var hand = hands.get(playerId);
        if (hand == null) {
            throw new IllegalArgumentException("Player not in round: " + playerId);
        }
        return hand;
    }

    public Optional<Teams> teams() {
        return Optional.ofNullable(teams);
    }

    public RoundStatus status() {
        return status;
    }

    public Optional<RoundResult> result() {
        return Optional.ofNullable(result);
    }

    public boolean isWaitingForSoledad() {
        return status == RoundStatus.WAITING_FOR_SOLEDAD;
    }

    public boolean isWaitingForTrump() {
        return status == RoundStatus.WAITING_FOR_TRUMP;
    }

    public boolean isInProgress() {
        return status == RoundStatus.IN_PROGRESS;
    }

    public boolean isComplete() {
        return status == RoundStatus.COMPLETE;
    }

    public Set<PlayerId> soledadPasses() {
        return soledadPasses;
    }

    public Optional<PlayerId> soledadPlayer() {
        return Optional.ofNullable(soledadPlayer);
    }

    public Optional<Instant> soledadDeadline() {
        return Optional.ofNullable(soledadDeadline);
    }

    public int getBasaCount() {
        return completedBasas.size() + (currentBasa != null ? 1 : 0);
    }

    /**
     * Returns the number of basas won by a player.
     */
    public int basasWonBy(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return (int) completedBasas.stream()
            .filter(b -> b.winner().map(w -> w.equals(playerId)).orElse(false))
            .count();
    }

    /**
     * Returns the basas won by each player.
     */
    public Map<PlayerId, Integer> basasWonByAll() {
        final var result = new HashMap<PlayerId, Integer>();
        for (final var player : playerOrder) {
            result.put(player, basasWonBy(player));
        }
        return Map.copyOf(result);
    }

    /**
     * Returns whose turn it is to play, or empty if not in a playable state.
     */
    public Optional<PlayerId> currentPlayer() {
        if (status != RoundStatus.IN_PROGRESS || currentBasa == null) {
            return Optional.empty();
        }

        final var cardsPlayed = currentBasa.cardCount();

        if (cardsPlayed >= Basa.CARDS_PER_BASA) {
            return Optional.empty();
        }

        final var starterIndex = playerOrder.indexOf(currentBasa.startingPlayer());
        final var currentIndex = (starterIndex + cardsPlayed) % PLAYER_COUNT;

        return Optional.of(playerOrder.get(currentIndex));
    }

    /**
     * Returns the player who should start the next basa.
     * The next basa starts with the player to the RIGHT of the previous basa's starter.
     */
    public PlayerId getNextBasaStarter() {
        if (completedBasas.isEmpty()) {
            return playerWhoGoes;
        }

        final var lastBasa = completedBasas.get(completedBasas.size() - 1);
        return getPlayerToRight(lastBasa.startingPlayer());
    }

    // === Mutation Methods (return new instances) ===

    /**
     * Records a player passing on Soledad.
     * If all 5 players pass, transitions to WAITING_FOR_TRUMP.
     */
    public Round withSoledadPass(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (status != RoundStatus.WAITING_FOR_SOLEDAD) {
            throw new IllegalStateException("Not waiting for soledad: " + status);
        }

        if (soledadPasses.contains(playerId)) {
            throw new IllegalStateException("Player already passed soledad: " + playerId);
        }

        if (!playerOrder.contains(playerId)) {
            throw new IllegalArgumentException("Player not in round: " + playerId);
        }

        final var newPasses = new HashSet<>(soledadPasses);
        newPasses.add(playerId);

        final var allPassed = newPasses.size() == PLAYER_COUNT;
        final var newStatus = allPassed ? RoundStatus.WAITING_FOR_TRUMP : RoundStatus.WAITING_FOR_SOLEDAD;

        return new Round(
            roundNumber,
            trumpSuit,
            playerWhoGoes,
            playerOrder,
            completedBasas,
            currentBasa,
            hands,
            teams,
            newStatus,
            result,
            newPasses,
            soledadPlayer,
            allPassed ? null : soledadDeadline
        );
    }

    /**
     * Records a player declaring Soledad.
     * Transitions to WAITING_FOR_TRUMP; the declaring player will choose trump and play 1v4.
     */
    public Round withSoledadDeclared(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (status != RoundStatus.WAITING_FOR_SOLEDAD) {
            throw new IllegalStateException("Not waiting for soledad: " + status);
        }

        if (!playerOrder.contains(playerId)) {
            throw new IllegalArgumentException("Player not in round: " + playerId);
        }

        return new Round(
            roundNumber,
            trumpSuit,
            playerId,
            playerOrder,
            completedBasas,
            currentBasa,
            hands,
            teams,
            RoundStatus.WAITING_FOR_TRUMP,
            result,
            soledadPasses,
            playerId,
            null
        );
    }

    /**
     * Sets the trump suit and starts the first basa.
     *
     * @param trump the trump suit selected by playerWhoGoes
     * @return new Round with trump set and first basa started
     */
    public Round withTrump(Suit trump) {
        Objects.requireNonNull(trump, "trump must not be null");

        if (status != RoundStatus.WAITING_FOR_TRUMP) {
            throw new IllegalStateException("Trump already selected or round complete");
        }

        final var firstBasa = Basa.start(1, playerWhoGoes);

        return new Round(
            roundNumber,
            trump,
            playerWhoGoes,
            playerOrder,
            completedBasas,
            firstBasa,
            hands,
            teams,
            RoundStatus.IN_PROGRESS,
            result,
            soledadPasses,
            soledadPlayer,
            null
        );
    }

    /**
     * Plays a card in the current basa.
     *
     * @param playerId the player playing the card
     * @param card the card to play
     * @param playedAt when the card was played
     * @return new Round with the card played
     */
    public Round withCardPlayed(PlayerId playerId, Card card, Instant playedAt) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(playedAt, "playedAt must not be null");

        if (status != RoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot play card: round is " + status);
        }
        if (currentBasa == null) {
            throw new IllegalStateException("No current basa to play in");
        }

        final var expectedPlayer = currentPlayer().orElseThrow(
            () -> new IllegalStateException("Basa is full, cannot play more cards")
        );
        if (!expectedPlayer.equals(playerId)) {
            throw new IllegalStateException(
                "Not player's turn: expected " + expectedPlayer + ", got " + playerId
            );
        }

        final var hand = hands.get(playerId);
        if (!hand.contains(card)) {
            throw new IllegalStateException("Player does not have card: " + card);
        }

        final var playedCard = PlayedCard.of(playerId, card, playedAt);
        final var newBasa = currentBasa.withCard(playedCard);
        final var newHands = removeCardFromHand(playerId, card);

        return new Round(
            roundNumber,
            trumpSuit,
            playerWhoGoes,
            playerOrder,
            completedBasas,
            newBasa,
            newHands,
            teams,
            status,
            result,
            soledadPasses,
            soledadPlayer,
            soledadDeadline
        );
    }

    /**
     * Completes the current basa with the determined winner.
     * Starts the next basa or ends the round if a team has won or all basas are played.
     *
     * @param winnerId the player who won the current basa
     * @return new Round with the basa completed
     */
    public Round completeBasa(PlayerId winnerId) {
        Objects.requireNonNull(winnerId, "winnerId must not be null");

        if (currentBasa == null) {
            throw new IllegalStateException("No current basa to complete");
        }
        if (currentBasa.cardCount() != Basa.CARDS_PER_BASA) {
            throw new IllegalStateException(
                "Current basa not ready to complete: need " + Basa.CARDS_PER_BASA +
                " cards, have " + currentBasa.cardCount()
            );
        }

        final var completedBasa = currentBasa.complete(winnerId);
        final var newCompletedBasas = new ArrayList<>(completedBasas);
        newCompletedBasas.add(completedBasa);

        final var roundResult = checkForRoundEnd(newCompletedBasas);

        if (roundResult.isPresent()) {
            return new Round(
                roundNumber,
                trumpSuit,
                playerWhoGoes,
                playerOrder,
                List.copyOf(newCompletedBasas),
                null,
                hands,
                teams,
                RoundStatus.COMPLETE,
                roundResult.get(),
                soledadPasses,
                soledadPlayer,
                soledadDeadline
            );
        }

        final var nextBasaNumber = newCompletedBasas.size() + 1;
        final var nextStarter = getNextBasaStarter(newCompletedBasas);
        final var nextBasa = Basa.start(nextBasaNumber, nextStarter);

        return new Round(
            roundNumber,
            trumpSuit,
            playerWhoGoes,
            playerOrder,
            List.copyOf(newCompletedBasas),
            nextBasa,
            hands,
            teams,
            status,
            result,
            soledadPasses,
            soledadPlayer,
            soledadDeadline
        );
    }

    /**
     * Sets the teams when the first King is played.
     *
     * @param newTeams the team assignment
     * @return new Round with teams set
     */
    public Round withTeams(Teams newTeams) {
        Objects.requireNonNull(newTeams, "teams must not be null");

        if (teams != null) {
            throw new IllegalStateException("Teams already set");
        }

        if (!newTeams.isOnTeamOfTwo(playerWhoGoes)) {
            throw new IllegalArgumentException("playerWhoGoes must be on team of two");
        }

        return new Round(
            roundNumber,
            trumpSuit,
            playerWhoGoes,
            playerOrder,
            completedBasas,
            currentBasa,
            hands,
            newTeams,
            status,
            result,
            soledadPasses,
            soledadPlayer,
            soledadDeadline
        );
    }

    // === Internal Helpers ===

    private PlayerId getPlayerToRight(PlayerId player) {
        final var index = playerOrder.indexOf(player);
        final var rightIndex = (index + 1) % PLAYER_COUNT;
        return playerOrder.get(rightIndex);
    }

    private PlayerId getNextBasaStarter(List<Basa> basas) {
        if (basas.isEmpty()) {
            return playerWhoGoes;
        }
        final var lastBasa = basas.get(basas.size() - 1);
        return getPlayerToRight(lastBasa.startingPlayer());
    }

    private Map<PlayerId, List<Card>> removeCardFromHand(PlayerId playerId, Card card) {
        final var newHands = new HashMap<PlayerId, List<Card>>();
        for (final var entry : hands.entrySet()) {
            if (entry.getKey().equals(playerId)) {
                final var newHand = new ArrayList<>(entry.getValue());
                newHand.remove(card);
                newHands.put(entry.getKey(), List.copyOf(newHand));
            } else {
                newHands.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(newHands);
    }

    private Optional<RoundResult> checkForRoundEnd(List<Basa> basas) {
        if (teams != null) {
            final var basasWon = new HashMap<PlayerId, Integer>();
            for (final var basa : basas) {
                basa.winner().ifPresent(w ->
                    basasWon.merge(w, 1, Integer::sum)
                );
            }

            final var teamOfTwoBasas = teams.teamOfTwo().stream()
                .mapToInt(p -> basasWon.getOrDefault(p, 0))
                .sum();
            final var teamOfThreeBasas = teams.teamOfThree().stream()
                .mapToInt(p -> basasWon.getOrDefault(p, 0))
                .sum();

            if (teamOfTwoBasas >= BASAS_TO_WIN) {
                return Optional.of(new RoundResult.Win(teams.teamOfTwo()));
            }
            if (teamOfThreeBasas >= BASAS_TO_WIN) {
                return Optional.of(new RoundResult.Win(teams.teamOfThree()));
            }
        }

        if (basas.size() >= MAX_BASAS) {
            return Optional.of(new RoundResult.Draw());
        }

        return Optional.empty();
    }

    private static Map<PlayerId, List<Card>> deepCopyHands(Map<PlayerId, List<Card>> original) {
        final var copy = new HashMap<PlayerId, List<Card>>();
        for (final var entry : original.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
