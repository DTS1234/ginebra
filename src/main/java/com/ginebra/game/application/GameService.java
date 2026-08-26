package com.ginebra.game.application;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.*;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.MoveValidation;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.SoledadUseCase;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.game.port.out.GameRepository;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service that orchestrates game operations.
 * Bridges the immutable domain model with repository persistence and event publishing.
 *
 * Thread safety: synchronizes on per-game locks to ensure atomic read-modify-write cycles.
 */
@Service
public class GameService implements StartGameUseCase, SelectTrumpUseCase, PlayCardUseCase, SoledadUseCase {

    private final GameRepository gameRepository;
    private final GameEventPublisher eventPublisher;
    private final MoveValidator moveValidator;
    private final BasaResolver basaResolver;
    private final Clock clock;
    private final Random random;
    private final ConcurrentHashMap<GameId, Object> gameLocks = new ConcurrentHashMap<>();

    public GameService(
        GameRepository gameRepository,
        GameEventPublisher eventPublisher,
        MoveValidator moveValidator,
        BasaResolver basaResolver,
        Clock clock,
        Random random
    ) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.moveValidator = Objects.requireNonNull(moveValidator, "moveValidator must not be null");
        this.basaResolver = Objects.requireNonNull(basaResolver, "basaResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public StartGameResult startGame(StartGameCommand command) {
        final var gameId = command.gameId();
        final var lock = gameLocks.computeIfAbsent(gameId, k -> new Object());

        synchronized (lock) {
            if (gameRepository.findById(gameId).isPresent()) {
                return new StartGameResult.GameAlreadyExists();
            }

            final var game = Game.start(gameId, command.players(), random, clock.instant());
            gameRepository.save(game);
            return new StartGameResult.Success(gameId);
        }
    }

    @Override
    public PassSoledadResult passSoledad(PassSoledadCommand command) {
        final var gameId = command.gameId();
        final var playerId = command.playerId();
        final var lock = gameLocks.computeIfAbsent(gameId, k -> new Object());

        synchronized (lock) {
            final var gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                return new PassSoledadResult.GameNotFound();
            }

            final var game = gameOpt.get();
            final var round = game.currentRound().orElse(null);
            if (round == null) {
                return new PassSoledadResult.InvalidGameState("No current round");
            }

            if (!round.isWaitingForSoledad()) {
                return new PassSoledadResult.WindowClosed();
            }

            if (round.soledadPasses().contains(playerId)) {
                return new PassSoledadResult.AlreadyPassed();
            }

            final var balancesBefore = game.coinBalances();
            var updatedGame = game.passSoledad(playerId);
            final var updatedRound = updatedGame.currentRound().orElseThrow();

            // Compute remaining players who haven't passed
            final var remaining = updatedRound.playerOrder().stream()
                .filter(p -> !updatedRound.soledadPasses().contains(p))
                .toList();

            eventPublisher.publishToGame(gameId, new GameEvent.SoledadPassed(playerId, remaining));

            // A four-king holder who declines to play the hand out takes their 4 and ends it.
            if (updatedRound.isComplete()) {
                finishRound(gameId, updatedGame, balancesBefore);
                return new PassSoledadResult.Success();
            }

            // If all passed, transition to WAITING_FOR_TRUMP
            if (updatedRound.isWaitingForTrump()) {
                eventPublisher.publishToGame(gameId, new GameEvent.SoledadWindowClosed(
                    false,
                    updatedRound.trumpChooser()
                ));
            }

            gameRepository.save(updatedGame);
            return new PassSoledadResult.Success();
        }
    }

    @Override
    public DeclareSoledadResult declareSoledad(DeclareSoledadCommand command) {
        final var gameId = command.gameId();
        final var playerId = command.playerId();
        final var lock = gameLocks.computeIfAbsent(gameId, k -> new Object());

        synchronized (lock) {
            final var gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                return new DeclareSoledadResult.GameNotFound();
            }

            final var game = gameOpt.get();
            final var round = game.currentRound().orElse(null);
            if (round == null) {
                return new DeclareSoledadResult.InvalidGameState("No current round");
            }

            if (!round.isWaitingForSoledad()) {
                return new DeclareSoledadResult.WindowClosed();
            }

            var updatedGame = game.declareSoledad(playerId);
            final var updatedRound = updatedGame.currentRound().orElseThrow();

            eventPublisher.publishToGame(gameId, new GameEvent.SoledadDeclared(playerId));
            eventPublisher.publishToGame(gameId, new GameEvent.SoledadWindowClosed(
                true,
                updatedRound.trumpChooser()
            ));

            gameRepository.save(updatedGame);
            return new DeclareSoledadResult.Success();
        }
    }

    @Override
    public SelectTrumpResult selectTrump(SelectTrumpCommand command) {
        final var gameId = command.gameId();
        final var lock = gameLocks.computeIfAbsent(gameId, k -> new Object());

        synchronized (lock) {
            final var gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                return new SelectTrumpResult.GameNotFound();
            }

            final var game = gameOpt.get();
            final var round = game.currentRound().orElse(null);
            if (round == null || !round.isWaitingForTrump()) {
                return new SelectTrumpResult.InvalidGameState("Game is not waiting for trump selection");
            }

            if (!round.trumpChooser().equals(command.playerId())) {
                return new SelectTrumpResult.NotYourTurn();
            }

            final var updatedGame = game.selectTrump(command.suit());
            gameRepository.save(updatedGame);

            final var currentTurn = updatedGame.currentRound()
                .flatMap(Round::currentPlayer)
                .orElse(null);

            eventPublisher.publishToGame(gameId, new GameEvent.TrumpSelected(
                command.playerId(),
                command.suit(),
                currentTurn
            ));

            return new SelectTrumpResult.Success();
        }
    }

    @Override
    public PlayCardResult playCard(PlayCardCommand command) {
        final var gameId = command.gameId();
        final var playerId = command.playerId();
        final var card = command.card();
        final var lock = gameLocks.computeIfAbsent(gameId, k -> new Object());

        synchronized (lock) {
            final var gameOpt = gameRepository.findById(gameId);
            if (gameOpt.isEmpty()) {
                return new PlayCardResult.GameNotFound();
            }

            var game = gameOpt.get();
            final var round = game.currentRound().orElse(null);
            if (round == null || !round.isInProgress()) {
                return new PlayCardResult.InvalidGameState("Game is not in a playable state");
            }

            // Validate turn
            final var currentPlayer = round.currentPlayer().orElse(null);
            if (!playerId.equals(currentPlayer)) {
                return new PlayCardResult.NotYourTurn();
            }

            // Validate move
            final var trumpSuit = round.trumpSuit().orElseThrow();
            final var hand = round.getHand(playerId);
            final var basa = round.currentBasa().orElseThrow();
            final var firstCard = basa.cardsPlayed().isEmpty()
                ? Optional.<Card>empty()
                : Optional.of(basa.cardsPlayed().get(0).card());

            final var leadContext = new MoveValidator.LeadContext(
                round.ledSuits(), round.sideDecided()
            );

            final var validation = moveValidator.validate(hand, card, trumpSuit, firstCard, leadContext);
            if (validation instanceof MoveValidation.Invalid invalid) {
                return new PlayCardResult.InvalidCard(invalid.code(), invalid.message());
            }

            // "Et cau el rei": a king with no legal alternative is put unintentionally,
            // and costs its owner 1. Decided against the hand as it stands before the play.
            final var kingWasForced = card.isKing()
                && noOtherLegalCard(hand, card, trumpSuit, firstCard, leadContext);

            // Capture pre-round balances before playing card (for coin delta calculation)
            final var preRoundBalances = game.coinBalances();

            // Play the card
            game = game.playCard(playerId, card, clock.instant());

            final var updatedRound = game.currentRound().orElseThrow();
            final var updatedBasa = updatedRound.currentBasa().orElse(null);

            // Publish card played
            final var nextTurn = updatedRound.currentPlayer().orElse(null);
            eventPublisher.publishToGame(gameId, new GameEvent.CardPlayed(playerId, card, nextTurn));

            // A king decides the shape of the round - who goes with whom, or alone.
            if (card.isKing() && updatedRound.mode().isEmpty()) {
                game = game.resolveKing(playerId, kingWasForced);
                final var decided = game.currentRound().orElseThrow();

                eventPublisher.publishToGame(gameId, new GameEvent.SideDecided(
                    decided.mode().orElseThrow(),
                    decided.goingSide(),
                    decided.opposingSide(),
                    playerId,
                    card,
                    kingWasForced
                ));

                if (decided.isComplete()) {
                    // "Si es qui és mà li cau el rei s'acaba sa mà."
                    finishRound(gameId, game, preRoundBalances);
                    return new PlayCardResult.Success();
                }
            }

            // Check if basa is complete (5 cards played)
            if (updatedBasa != null && updatedBasa.cardCount() == Basa.CARDS_PER_BASA) {
                final var winnerId = basaResolver.resolveWinner(updatedBasa, trumpSuit);
                game = game.completeBasa(winnerId);

                final var afterBasa = game.currentRound().orElseThrow();

                eventPublisher.publishToGame(gameId, new GameEvent.BasaWon(
                    updatedBasa.basaNumber(),
                    winnerId,
                    updatedBasa.cardsPlayed(),
                    afterBasa.basasWonByAll(),
                    afterBasa.currentBasa().map(Basa::basaNumber).orElse(0),
                    afterBasa.currentBasa().map(Basa::startingPlayer).orElse(null)
                ));

                // Check if round ended
                if (afterBasa.isComplete()) {
                    finishRound(gameId, game, preRoundBalances);
                    return new PlayCardResult.Success();
                }
            }

            gameRepository.save(game);
            return new PlayCardResult.Success();
        }
    }

    /**
     * Announces a settled round, ends the game or deals the next one, and saves.
     *
     * The round has already been priced against the posso by the aggregate; this only
     * reports what moved and advances the game.
     */
    private void finishRound(
        GameId gameId,
        Game settled,
        Map<PlayerId, Integer> preRoundBalances
    ) {
        var game = settled;
        final var round = game.currentRound().orElseThrow();

        eventPublisher.publishToGame(gameId, new GameEvent.RoundEnded(
            round.roundNumber(),
            round.result().orElseThrow(),
            computeCoinDeltas(preRoundBalances, game.coinBalances()),
            game.coinBalances(),
            game.posso()
        ));

        if (game.isEnded()) {
            eventPublisher.publishToGame(gameId, new GameEvent.GameEnded(
                "PLAYER_OUT_OF_COINS",
                game.coinBalances()
            ));
            gameRepository.save(game);
            return;
        }

        game = game.startNextRound(random, clock.instant());

        gameRepository.save(game);
    }

    /**
     * Whether every other card in the hand would be rejected, leaving this one forced.
     */
    private boolean noOtherLegalCard(
        List<Card> hand,
        Card card,
        Suit trumpSuit,
        Optional<Card> firstCardInBasa,
        MoveValidator.LeadContext leadContext
    ) {
        return hand.stream()
            .filter(c -> !c.equals(card))
            .noneMatch(c -> moveValidator.validate(hand, c, trumpSuit, firstCardInBasa, leadContext)
                instanceof MoveValidation.Valid);
    }

    /**
     * Retrieves a game by ID. Used by connection tracking to send game state on subscribe.
     */
    public Optional<Game> getGame(GameId gameId) {
        return gameRepository.findById(gameId);
    }

    private Map<PlayerId, Integer> computeCoinDeltas(
        Map<PlayerId, Integer> preRoundBalances,
        Map<PlayerId, Integer> postRoundBalances
    ) {
        final var deltas = new HashMap<PlayerId, Integer>();
        for (final var entry : postRoundBalances.entrySet()) {
            final var pre = preRoundBalances.getOrDefault(entry.getKey(), 0);
            deltas.put(entry.getKey(), entry.getValue() - pre);
        }
        return Map.copyOf(deltas);
    }
}
