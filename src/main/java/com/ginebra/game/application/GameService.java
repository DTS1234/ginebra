package com.ginebra.game.application;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.*;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.MoveValidation;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.TeamResolver;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
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
public class GameService implements StartGameUseCase, SelectTrumpUseCase, PlayCardUseCase {

    private final GameRepository gameRepository;
    private final GameEventPublisher eventPublisher;
    private final MoveValidator moveValidator;
    private final BasaResolver basaResolver;
    private final TeamResolver teamResolver;
    private final Clock clock;
    private final Random random;
    private final ConcurrentHashMap<GameId, Object> gameLocks = new ConcurrentHashMap<>();

    public GameService(
        GameRepository gameRepository,
        GameEventPublisher eventPublisher,
        MoveValidator moveValidator,
        BasaResolver basaResolver,
        TeamResolver teamResolver,
        Clock clock,
        Random random
    ) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.moveValidator = Objects.requireNonNull(moveValidator, "moveValidator must not be null");
        this.basaResolver = Objects.requireNonNull(basaResolver, "basaResolver must not be null");
        this.teamResolver = Objects.requireNonNull(teamResolver, "teamResolver must not be null");
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

            final var game = Game.start(gameId, command.players(), random);
            gameRepository.save(game);
            return new StartGameResult.Success(gameId);
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

            if (!round.playerWhoGoes().equals(command.playerId())) {
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

            final var validation = moveValidator.validate(hand, card, trumpSuit, firstCard);
            if (validation instanceof MoveValidation.Invalid invalid) {
                return new PlayCardResult.InvalidCard(invalid.code(), invalid.message());
            }

            // Play the card
            game = game.playCard(playerId, card, clock.instant());

            final var updatedRound = game.currentRound().orElseThrow();
            final var updatedBasa = updatedRound.currentBasa().orElse(null);

            // Publish card played
            final var nextTurn = updatedRound.currentPlayer().orElse(null);
            eventPublisher.publishToGame(gameId, new GameEvent.CardPlayed(playerId, card, nextTurn));

            // Check team formation (first King triggers teams)
            if (updatedRound.teams().isEmpty()) {
                final var teamsOpt = teamResolver.resolveTeams(
                    card,
                    playerId,
                    updatedRound.playerWhoGoes(),
                    new HashSet<>(game.players())
                );
                if (teamsOpt.isPresent()) {
                    game = game.setTeams(teamsOpt.get());
                    final var teams = teamsOpt.get();
                    eventPublisher.publishToGame(gameId, new GameEvent.TeamsRevealed(
                        teams.teamOfTwo(),
                        teams.teamOfThree(),
                        card
                    ));
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
                    final var result = afterBasa.result().orElseThrow();
                    final var coinChanges = computeCoinChanges(game);

                    eventPublisher.publishToGame(gameId, new GameEvent.RoundEnded(
                        afterBasa.roundNumber(),
                        result,
                        coinChanges,
                        game.coinBalances()
                    ));

                    // Check if game ended
                    if (game.isEnded()) {
                        eventPublisher.publishToGame(gameId, new GameEvent.GameEnded(
                            "PLAYER_BANKRUPT",
                            game.coinBalances()
                        ));
                    } else {
                        // Start next round automatically
                        game = game.startNextRound(random);
                    }
                }
            }

            gameRepository.save(game);
            return new PlayCardResult.Success();
        }
    }

    /**
     * Retrieves a game by ID. Used by connection tracking to send game state on subscribe.
     */
    public Optional<Game> getGame(GameId gameId) {
        return gameRepository.findById(gameId);
    }

    private Map<PlayerId, Integer> computeCoinChanges(Game gameAfterBasa) {
        final var previousBalances = gameAfterBasa.coinBalances();
        // The coin changes are the difference between the game's current balances
        // and what they would be without the changes. Since Game.completeBasa already
        // applies coins, we need to compute the deltas.
        // For now, we emit the final balances in the event. The actual per-player
        // changes can be derived by the client.
        return previousBalances;
    }
}
