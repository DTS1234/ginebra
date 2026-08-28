package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a single round in the game.
 *
 * A round is up to 8 basas. The side that "goes" must make 5 of them; the opposing side
 * wins by holding them to 4 or fewer, which is decided the moment the opponents take their
 * 4th (rules-source.md §4.1). There is no draw.
 *
 * Which players make up the going side depends on the {@link RoundMode}, and is unknown
 * until a king is played or Soledad is declared.
 *
 * This class is immutable - mutation methods return new instances.
 */
public final class Round {

    public static final int MAX_BASAS = 8;
    /**
     * Five basas wins the hand - for <b>either</b> side. The opposing side could put five
     * out of the going side's reach with four, but the hand is played on to five all the
     * same (the players, 2026-08-27), which is why eight basas can finish 4-4.
     */
    public static final int BASAS_TO_WIN = 5;

    /** "Fer primeres": the first four basas, in a row. */
    public static final int PRIMERES_BASAS = 4;

    public static final int PLAYER_COUNT = 5;
    public static final int CARDS_PER_HAND = 8;
    public static final int TOTAL_CARDS = 40;
    public static final int KINGS_IN_DECK = 4;

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
    private final RoundMode mode;
    private final PlayerId soloPlayer;
    private final boolean firstKingCalled;
    private final PlayerId fourKingHolder;
    private final boolean todoCalled;

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
        Instant soledadDeadline,
        RoundMode mode,
        PlayerId soloPlayer,
        boolean firstKingCalled,
        PlayerId fourKingHolder,
        boolean todoCalled
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
        this.mode = mode;
        this.soloPlayer = soloPlayer;
        this.firstKingCalled = firstKingCalled;
        this.fourKingHolder = fourKingHolder;
        this.todoCalled = todoCalled;
    }

    // === Factory Methods ===

    /**
     * Creates a new round in the WAITING_FOR_SOLEDAD state.
     *
     * A deal that hands one player all four kings is theirs to decide: they may take the 4
     * and end the hand, or go alone and play it out, keeping the 4 either way. Nobody else
     * may declare against such a deal - <i>"sols pot anar a soles es qui té es quatre
     * reis"</i> (rules-source.md §4.8). The choice is made in the Soledad window.
     *
     * @param roundNumber the round number (1-based)
     * @param playerWhoGoes the player who is "mà" - who leads the first basa
     * @param playerOrder the 5 players in clockwise order
     * @param hands the cards dealt to each player (8 each)
     * @param soledadDeadline deadline for soledad declarations
     * @return a new Round, normally in WAITING_FOR_SOLEDAD status
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

        final var fourKingHolder = findFourKingHolder(hands);

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
            soledadDeadline,
            null,          // mode
            null,          // soloPlayer
            false,         // firstKingCalled
            fourKingHolder,
            false          // todoCalled
        );
    }

    /**
     * The player dealt all four kings, or null if nobody was.
     */
    private static PlayerId findFourKingHolder(Map<PlayerId, List<Card>> hands) {
        for (final var entry : hands.entrySet()) {
            final var kings = entry.getValue().stream().filter(Card::isKing).count();
            if (kings == KINGS_IN_DECK) {
                return entry.getKey();
            }
        }
        return null;
    }

    // === Query Methods ===

    public int roundNumber() {
        return roundNumber;
    }

    public Optional<Suit> trumpSuit() {
        return Optional.ofNullable(trumpSuit);
    }

    /**
     * The player who is "mà": the one who leads the first basa.
     *
     * They normally name trumps too, but not in a Soledad round - see
     * {@link #trumpChooser()}.
     */
    public PlayerId playerWhoGoes() {
        return playerWhoGoes;
    }

    /**
     * Who names the trump suit: the Soledad declarer if there is one, otherwise the mà.
     *
     * <i>"Després farà trumfos qui siga mà o qui vaja a soles, encara que no siga mà"</i>
     * (rules-source.md §4.1) - a lone player names trumps without being mà, and the mà
     * still leads.
     */
    public PlayerId trumpChooser() {
        return soledadPlayer != null ? soledadPlayer : playerWhoGoes;
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

    public Optional<RoundMode> mode() {
        return Optional.ofNullable(mode);
    }

    /**
     * The player dealt all four kings, if there was one. They collect 4 whatever they then
     * decide, and they are the only player who may declare Soledad in this round.
     */
    public Optional<PlayerId> fourKingHolder() {
        return Optional.ofNullable(fourKingHolder);
    }

    /**
     * Whether the one who goes has called "es primer rei aida" (rules-source.md §4.3).
     */
    public boolean firstKingCalled() {
        return firstKingCalled;
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

    /**
     * Whether play is paused on the going side's "fer todo" decision.
     */
    public boolean isWaitingForTodo() {
        return status == RoundStatus.WAITING_FOR_TODO;
    }

    /**
     * Whether play is paused on the one who goes deciding what to do about their own king
     * having been forced out of them.
     */
    public boolean isWaitingForKingChoice() {
        return status == RoundStatus.WAITING_FOR_KING_CHOICE;
    }

    /**
     * Whether "fer todo" was called. Called and missed costs the going side a coin.
     */
    public boolean todoCalled() {
        return todoCalled;
    }

    /**
     * Whose call "fer todo" is: the one who goes, or the lone player when they go alone.
     */
    public Optional<PlayerId> todoCaller() {
        final var going = goingSide();
        if (going.isEmpty()) {
            return Optional.empty();
        }
        return going.contains(playerWhoGoes)
            ? Optional.of(playerWhoGoes)
            : going.stream().findFirst();
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
     * The players on the side that goes, or empty while the side is still unknown.
     */
    public Set<PlayerId> goingSide() {
        if (mode == null) {
            return Set.of();
        }
        return switch (mode) {
            case HELPED -> teams != null ? teams.teamOfTwo() : Set.of();
            case SELF_KING, SOLEDAD, FOUR_KINGS -> Set.of(soloPlayer);
            case KING_FELL -> Set.of();
        };
    }

    /**
     * The players opposing the side that goes, or empty while the side is still unknown.
     */
    public Set<PlayerId> opposingSide() {
        final var going = goingSide();
        if (going.isEmpty()) {
            return Set.of();
        }
        return playerOrder.stream()
            .filter(p -> !going.contains(p))
            .collect(Collectors.toUnmodifiableSet());
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
     * Basas taken by the side that goes.
     */
    public int goingSideBasas() {
        return basasWonByAny(goingSide(), completedBasas);
    }

    /**
     * Whether the going side took the first four basas in a row: "fer primeres".
     */
    public boolean madePrimeres() {
        final var going = goingSide();
        if (going.isEmpty() || completedBasas.size() < PRIMERES_BASAS) {
            return false;
        }
        return completedBasas.subList(0, PRIMERES_BASAS).stream()
            .allMatch(b -> b.winner().map(going::contains).orElse(false));
    }

    /**
     * Whether the going side took every basa: "fer todo". Contains primeres by definition.
     */
    public boolean madeTodo() {
        final var going = goingSide();
        if (going.isEmpty() || completedBasas.size() < MAX_BASAS) {
            return false;
        }
        return completedBasas.stream()
            .allMatch(b -> b.winner().map(going::contains).orElse(false));
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
     * Every effective suit led so far in this round.
     *
     * A special card leading counts as leading trump, the same way it does everywhere else.
     * While no King has appeared the leader must open with a suit that is <b>not</b> in
     * this set, if they hold one - that is how the King gets smoked out.
     */
    public Set<Suit> ledSuits() {
        if (trumpSuit == null) {
            return Set.of();
        }
        return completedBasas.stream()
            .flatMap(basa -> basa.cardsPlayed().stream().limit(1))
            .map(played -> com.ginebra.game.domain.service.MoveValidator
                .effectiveLedSuit(played.card(), trumpSuit))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether a King has appeared and settled who goes with whom.
     */
    public boolean sideDecided() {
        return mode != null;
    }

    /**
     * Returns the player who should start the next basa.
     * The winner of a basa leads the next one; the first basa is led by the player who goes.
     *
     * Note: rotation to the right applies between rounds, not between basas - see
     * {@link Game#startNextRound}.
     */
    public PlayerId getNextBasaStarter() {
        return getNextBasaStarter(completedBasas);
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

        // A four-king deal is nobody else's decision: once the holder declines to play it
        // out, they take their 4 and the hand is over.
        if (playerId.equals(fourKingHolder)) {
            return copy()
                .status(RoundStatus.COMPLETE)
                .soledadPasses(newPasses)
                .soledadDeadline(null)
                .mode(RoundMode.FOUR_KINGS)
                .soloPlayer(fourKingHolder)
                .result(new RoundResult.FourKings(fourKingHolder))
                .build();
        }

        final var allPassed = newPasses.size() == PLAYER_COUNT;
        final var newStatus = allPassed ? RoundStatus.WAITING_FOR_TRUMP : RoundStatus.WAITING_FOR_SOLEDAD;

        return copy()
            .status(newStatus)
            .soledadPasses(newPasses)
            .soledadDeadline(allPassed ? null : soledadDeadline)
            .build();
    }

    /**
     * Records a player declaring Soledad.
     *
     * The declarer names trumps and plays 1 against 4, but does <b>not</b> take over the
     * lead: the mà still leads the first basa (rules-source.md §4.1).
     */
    public Round withSoledadDeclared(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (status != RoundStatus.WAITING_FOR_SOLEDAD) {
            throw new IllegalStateException("Not waiting for soledad: " + status);
        }

        if (!playerOrder.contains(playerId)) {
            throw new IllegalArgumentException("Player not in round: " + playerId);
        }

        if (fourKingHolder != null && !playerId.equals(fourKingHolder)) {
            throw new IllegalStateException(
                "Only the player dealt the four kings may go alone this round: " + fourKingHolder
            );
        }

        return copy()
            .status(RoundStatus.WAITING_FOR_TRUMP)
            .soledadPlayer(playerId)
            .soledadDeadline(null)
            .mode(RoundMode.SOLEDAD)
            .soloPlayer(playerId)
            .build();
    }

    /**
     * Records the one who goes calling "es primer rei aida" (rules-source.md §4.3): a call
     * for whoever holds a king to put it as early as they can.
     */
    public Round withFirstKingCalled() {
        if (status != RoundStatus.IN_PROGRESS && status != RoundStatus.WAITING_FOR_TRUMP) {
            throw new IllegalStateException("Cannot call the first king now: " + status);
        }
        if (mode != null) {
            throw new IllegalStateException("The side is already decided: " + mode);
        }
        return copy().firstKingCalled(true).build();
    }

    /**
     * Sets the trump suit and starts the first basa, led by the mà.
     *
     * @param trump the trump suit selected by {@link #trumpChooser()}
     * @return new Round with trump set and first basa started
     */
    public Round withTrump(Suit trump) {
        Objects.requireNonNull(trump, "trump must not be null");

        if (status != RoundStatus.WAITING_FOR_TRUMP) {
            throw new IllegalStateException("Trump already selected or round complete");
        }

        return copy()
            .trumpSuit(trump)
            .currentBasa(Basa.start(1, playerWhoGoes))
            .status(RoundStatus.IN_PROGRESS)
            .soledadDeadline(null)
            .build();
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

        return copy()
            .currentBasa(currentBasa.withCard(PlayedCard.of(playerId, card, playedAt)))
            .hands(removeCardFromHand(playerId, card))
            .build();
    }

    /**
     * Records the king that decides the round's shape (rules-source.md §4.3).
     *
     * Three outcomes, depending on who played it and whether they had a choice:
     * <ul>
     *   <li>another player, by choice or forced - they aid, and the round is HELPED;</li>
     *   <li>the mà, by choice - "posar-se el rei", the round is SELF_KING and they play
     *       one against four;</li>
     *   <li>the mà, forced - "si es qui és mà li cau el rei s'acaba sa mà": the hand ends
     *       there with no side ever formed.</li>
     * </ul>
     *
     * A forced king costs its owner 1 whoever they are.
     *
     * @param playerId the player who played the king
     * @param forced whether they had no legal alternative
     */
    public Round withKingPlayed(PlayerId playerId, boolean forced) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (mode != null) {
            throw new IllegalStateException("The side is already decided: " + mode);
        }
        if (!playerOrder.contains(playerId)) {
            throw new IllegalArgumentException("Player not in round: " + playerId);
        }

        // A king forced out of anyone else costs them nothing: they are the helper now,
        // whether they meant to be or not, and play the hand for the helper's stake.
        if (!playerId.equals(playerWhoGoes)) {
            return copy()
                .mode(RoundMode.HELPED)
                .teams(Teams.of(playerWhoGoes, playerId, new HashSet<>(playerOrder)))
                .build();
        }

        // The one who goes putting their own king down deliberately has already chosen to
        // play it out alone. Having it dragged out of them has not: they are asked.
        if (forced) {
            return copy()
                .status(RoundStatus.WAITING_FOR_KING_CHOICE)
                .build();
        }

        return copy()
            .mode(RoundMode.SELF_KING)
            .soloPlayer(playerWhoGoes)
            .build();
    }

    /**
     * Answers the question a forced king asks the one who goes: carry on alone, or stop.
     *
     * <i>"Si te cae el rey cuando vas, puedes elegir si quieres seguir (si tienes cartas
     * buenas para hacer 5) o parar si no lo tienes bueno"</i> (the players, 2026-08-27).
     * Carrying on is one against four for 4 either way; stopping costs 1 and ends the
     * hand there, with nobody else paying or collecting.
     */
    public Round withKingChoice(PlayerId playerId, boolean carryOn) {
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (status != RoundStatus.WAITING_FOR_KING_CHOICE) {
            throw new IllegalStateException("No king to decide on: " + status);
        }
        if (!playerId.equals(playerWhoGoes)) {
            throw new IllegalArgumentException(
                "Only the one who goes decides on their own king: " + playerWhoGoes
            );
        }

        if (!carryOn) {
            return copy()
                .mode(RoundMode.KING_FELL)
                .status(RoundStatus.COMPLETE)
                .currentBasa(null)
                .result(new RoundResult.KingFell(playerWhoGoes))
                .build();
        }

        // The basa the king fell in carries on from where it stopped - the cards already
        // committed to it stay on the table.
        return copy()
            .mode(RoundMode.SELF_KING)
            .soloPlayer(playerWhoGoes)
            .status(RoundStatus.IN_PROGRESS)
            .build();
    }

    /**
     * Records the going side calling "fer todo": play continues for all eight basas.
     *
     * Making it is worth a coin, missing it costs one - confirmed by the players
     * 2026-08-26 - so the call is a real gamble and has to be theirs to make.
     */
    public Round withTodoCalled(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        requireTodoDecisionFrom(playerId);

        final var nextBasaNumber = completedBasas.size() + 1;

        return copy()
            .todoCalled(true)
            .status(RoundStatus.IN_PROGRESS)
            .currentBasa(Basa.start(nextBasaNumber, getNextBasaStarter(completedBasas)))
            .build();
    }

    /**
     * Records the going side declining "fer todo": the round ends on the win they have.
     */
    public Round withTodoDeclined(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        requireTodoDecisionFrom(playerId);

        return copy()
            .status(RoundStatus.COMPLETE)
            .result(new RoundResult.GoingSideWon(goingSide(), opposingSide(), goingSideBasas()))
            .build();
    }

    private void requireTodoDecisionFrom(PlayerId playerId) {
        if (status != RoundStatus.WAITING_FOR_TODO) {
            throw new IllegalStateException("Not waiting for a todo decision: " + status);
        }
        final var caller = todoCaller().orElseThrow(
            () -> new IllegalStateException("No side to call todo")
        );
        if (!caller.equals(playerId)) {
            throw new IllegalStateException("Todo is " + caller + "'s call, not " + playerId + "'s");
        }
    }

    /**
     * Completes the current basa with the determined winner.
     * Starts the next basa or ends the round if it has been decided.
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
            return copy()
                .completedBasas(List.copyOf(newCompletedBasas))
                .currentBasa(null)
                .status(RoundStatus.COMPLETE)
                .result(roundResult.get())
                .build();
        }

        if (todoIsNowTheirCall(newCompletedBasas)) {
            return copy()
                .completedBasas(List.copyOf(newCompletedBasas))
                .currentBasa(null)
                .status(RoundStatus.WAITING_FOR_TODO)
                .build();
        }

        final var nextBasaNumber = newCompletedBasas.size() + 1;
        final var nextStarter = getNextBasaStarter(newCompletedBasas);

        return copy()
            .completedBasas(List.copyOf(newCompletedBasas))
            .currentBasa(Basa.start(nextBasaNumber, nextStarter))
            .build();
    }

    /**
     * Sets the teams when the first King is played.
     *
     * @param newTeams the team assignment
     * @return new Round with teams set
     * @deprecated superseded by {@link #withKingPlayed(PlayerId, boolean)}, which also
     *             records the mode and handles the two solo cases. Kept because it states
     *             the 2-vs-3 invariant more directly than the king rules do.
     */
    @Deprecated
    public Round withTeams(Teams newTeams) {
        Objects.requireNonNull(newTeams, "teams must not be null");

        if (teams != null) {
            throw new IllegalStateException("Teams already set");
        }

        if (mode != null) {
            throw new IllegalStateException("The side is already decided: " + mode);
        }

        if (!newTeams.isOnTeamOfTwo(playerWhoGoes)) {
            throw new IllegalArgumentException("playerWhoGoes must be on team of two");
        }

        return copy().teams(newTeams).mode(RoundMode.HELPED).build();
    }

    // === Internal Helpers ===

    private PlayerId getNextBasaStarter(List<Basa> basas) {
        if (basas.isEmpty()) {
            return playerWhoGoes;
        }
        final var lastBasa = basas.get(basas.size() - 1);
        return lastBasa.winner().orElseThrow(
            () -> new IllegalStateException("Completed basa has no winner: " + lastBasa.basaNumber())
        );
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

    private static int basasWonByAny(Set<PlayerId> side, List<Basa> basas) {
        if (side.isEmpty()) {
            return 0;
        }
        return (int) basas.stream()
            .filter(b -> b.winner().map(side::contains).orElse(false))
            .count();
    }

    /**
     * Decides the round, or returns empty to play on.
     *
     * Five basas decides it, for whichever side gets there first. The opposing side's
     * fourth already puts five beyond the going side, but the hand is played on - so a
     * round can run all eight basas and finish 4-4, which is the going side falling short
     * of its five like any other failure.
     *
     * A going side that reaches 5 having won <i>every</i> basa so far plays on: "fer todo"
     * is still live and worth another coin. Dropping one settles it immediately, because
     * there is then nothing left to play for.
     */
    private Optional<RoundResult> checkForRoundEnd(List<Basa> basas) {
        final var going = goingSide();

        if (going.isEmpty()) {
            if (basas.size() >= MAX_BASAS) {
                throw new IllegalStateException(
                    "All basas played but no side was ever formed in round " + roundNumber
                );
            }
            return Optional.empty();
        }

        final var opposing = opposingSide();
        final var goingBasas = basasWonByAny(going, basas);
        final var opposingBasas = basasWonByAny(opposing, basas);

        if (goingBasas >= BASAS_TO_WIN) {
            if (todoIsStillReachable(basas)) {
                // Either theirs to call, or already called and being played out.
                return Optional.empty();
            }
            return Optional.of(new RoundResult.GoingSideWon(going, opposing, goingBasas));
        }

        if (opposingBasas >= BASAS_TO_WIN || basas.size() >= MAX_BASAS) {
            return Optional.of(new RoundResult.GoingSideFailed(going, opposing, goingBasas));
        }

        return Optional.empty();
    }

    /**
     * Whether the going side still holds every basa played and there are basas left, which
     * is the only state in which "fer todo" is worth anything.
     */
    private boolean todoIsStillReachable(List<Basa> basas) {
        return basasWonByAny(goingSide(), basas) == basas.size() && basas.size() < MAX_BASAS;
    }

    /**
     * Whether the going side has just reached five with a clean sweep and has not yet
     * decided whether to go for todo.
     */
    private boolean todoIsNowTheirCall(List<Basa> basas) {
        return !todoCalled
            && basasWonByAny(goingSide(), basas) >= BASAS_TO_WIN
            && todoIsStillReachable(basas);
    }

    private static Map<PlayerId, List<Card>> deepCopyHands(Map<PlayerId, List<Card>> original) {
        final var copy = new HashMap<PlayerId, List<Card>>();
        for (final var entry : original.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    // === Copy-on-write builder ===

    private Builder copy() {
        return new Builder(this);
    }

    /**
     * Internal builder so a mutation names only the fields it changes. Round has enough
     * state that positional constructor calls stopped being readable.
     */
    private static final class Builder {
        private int roundNumber;
        private Suit trumpSuit;
        private PlayerId playerWhoGoes;
        private List<PlayerId> playerOrder;
        private List<Basa> completedBasas;
        private Basa currentBasa;
        private Map<PlayerId, List<Card>> hands;
        private Teams teams;
        private RoundStatus status;
        private RoundResult result;
        private Set<PlayerId> soledadPasses;
        private PlayerId soledadPlayer;
        private Instant soledadDeadline;
        private RoundMode mode;
        private PlayerId soloPlayer;
        private boolean firstKingCalled;
        private PlayerId fourKingHolder;
        private boolean todoCalled;

        private Builder(Round from) {
            this.roundNumber = from.roundNumber;
            this.trumpSuit = from.trumpSuit;
            this.playerWhoGoes = from.playerWhoGoes;
            this.playerOrder = from.playerOrder;
            this.completedBasas = from.completedBasas;
            this.currentBasa = from.currentBasa;
            this.hands = from.hands;
            this.teams = from.teams;
            this.status = from.status;
            this.result = from.result;
            this.soledadPasses = from.soledadPasses;
            this.soledadPlayer = from.soledadPlayer;
            this.soledadDeadline = from.soledadDeadline;
            this.mode = from.mode;
            this.soloPlayer = from.soloPlayer;
            this.firstKingCalled = from.firstKingCalled;
            this.fourKingHolder = from.fourKingHolder;
            this.todoCalled = from.todoCalled;
        }

        private Builder trumpSuit(Suit v) { this.trumpSuit = v; return this; }
        private Builder completedBasas(List<Basa> v) { this.completedBasas = v; return this; }
        private Builder currentBasa(Basa v) { this.currentBasa = v; return this; }
        private Builder hands(Map<PlayerId, List<Card>> v) { this.hands = v; return this; }
        private Builder teams(Teams v) { this.teams = v; return this; }
        private Builder status(RoundStatus v) { this.status = v; return this; }
        private Builder result(RoundResult v) { this.result = v; return this; }
        private Builder soledadPasses(Set<PlayerId> v) { this.soledadPasses = v; return this; }
        private Builder soledadPlayer(PlayerId v) { this.soledadPlayer = v; return this; }
        private Builder soledadDeadline(Instant v) { this.soledadDeadline = v; return this; }
        private Builder mode(RoundMode v) { this.mode = v; return this; }
        private Builder soloPlayer(PlayerId v) { this.soloPlayer = v; return this; }
        private Builder firstKingCalled(boolean v) { this.firstKingCalled = v; return this; }
        private Builder todoCalled(boolean v) { this.todoCalled = v; return this; }

        private Round build() {
            return new Round(
                roundNumber, trumpSuit, playerWhoGoes, playerOrder, completedBasas, currentBasa,
                hands, teams, status, result, soledadPasses, soledadPlayer, soledadDeadline,
                mode, soloPlayer, firstKingCalled, fourKingHolder, todoCalled
            );
        }
    }
}
