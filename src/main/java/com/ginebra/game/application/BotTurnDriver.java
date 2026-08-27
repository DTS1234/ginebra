package com.ginebra.game.application;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.service.BotStrategy;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.SoledadUseCase;
import com.ginebra.game.port.in.TodoUseCase;
import com.ginebra.game.port.out.GameRepository;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Takes the turns of the seats nobody is sitting in.
 *
 * A human's move arrives, is applied, and control passes to whoever is next. If that is a
 * bot, nothing else in the system will move the game on - so every entry point that
 * changes a game calls {@link #drive} afterwards, and this plays out every bot decision
 * standing between there and the next thing a human has to do.
 *
 * It goes through the same use cases a human's client does. A bot cannot cheat, because
 * it has no way to: {@code MoveValidator} judges its card exactly as it judges anyone's,
 * and a bug in a strategy shows up as a rejected move rather than an illegal one.
 *
 * Moves are spaced out and run off the caller's thread. Instant bots would resolve a
 * whole basa inside the human's own click, so nobody would see the cards land.
 */
public class BotTurnDriver {

    private static final Logger log = LoggerFactory.getLogger(BotTurnDriver.class);

    /**
     * A stop, not a budget. Every action here advances the game, so the loop ends on its
     * own when a human is due; this only bounds the damage if some state ever fails to
     * advance, rather than spinning for as long as the server is up.
     */
    private static final int MAX_MOVES_PER_DRIVE = 500;

    private final GameRepository gameRepository;
    private final BotRoster roster;
    private final BotStrategy strategy;
    private final MoveValidator moveValidator;
    private final SoledadUseCase soledadUseCase;
    private final SelectTrumpUseCase selectTrumpUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final TodoUseCase todoUseCase;
    private final Executor executor;
    private final Duration moveDelay;

    /** One driver per game at a time, so two overlapping nudges do not both play. */
    private final ConcurrentHashMap<GameId, Boolean> driving = new ConcurrentHashMap<>();

    public BotTurnDriver(
        GameRepository gameRepository,
        BotRoster roster,
        BotStrategy strategy,
        MoveValidator moveValidator,
        SoledadUseCase soledadUseCase,
        SelectTrumpUseCase selectTrumpUseCase,
        PlayCardUseCase playCardUseCase,
        TodoUseCase todoUseCase,
        Executor botExecutor,
        Duration botMoveDelay
    ) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository must not be null");
        this.roster = Objects.requireNonNull(roster, "roster must not be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        this.moveValidator = Objects.requireNonNull(moveValidator, "moveValidator must not be null");
        this.soledadUseCase = Objects.requireNonNull(soledadUseCase, "soledadUseCase must not be null");
        this.selectTrumpUseCase = Objects.requireNonNull(selectTrumpUseCase, "selectTrumpUseCase must not be null");
        this.playCardUseCase = Objects.requireNonNull(playCardUseCase, "playCardUseCase must not be null");
        this.todoUseCase = Objects.requireNonNull(todoUseCase, "todoUseCase must not be null");
        this.executor = Objects.requireNonNull(botExecutor, "botExecutor must not be null");
        this.moveDelay = Objects.requireNonNull(botMoveDelay, "botMoveDelay must not be null");
    }

    /**
     * Nudges the game: if a bot is due, it plays, off this thread.
     *
     * Safe to call on any game, including one with no bots in it - it costs a lookup and
     * returns.
     */
    public void drive(GameId gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        executor.execute(() -> {
            try {
                driveNow(gameId);
            } catch (RuntimeException e) {
                log.warn("Bot turns for game {} stopped early", gameId.value(), e);
            }
        });
    }

    /**
     * Plays every bot decision that is pending, here and now, and returns how many were
     * made. Stops as soon as the game is waiting on a human, has ended, or refuses a move.
     */
    public int driveNow(GameId gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");

        if (driving.putIfAbsent(gameId, Boolean.TRUE) != null) {
            return 0;   // another thread is already playing this table out
        }

        try {
            var moves = 0;
            while (moves < MAX_MOVES_PER_DRIVE && actOnce(gameId)) {
                moves++;
            }
            if (moves == MAX_MOVES_PER_DRIVE) {
                log.warn("Bot turns for game {} hit the move ceiling", gameId.value());
            }
            return moves;
        } finally {
            driving.remove(gameId);
        }
    }

    /** One pending bot decision, or false if there is none to make. */
    private boolean actOnce(GameId gameId) {
        final var game = gameRepository.findById(gameId).orElse(null);
        if (game == null || game.isEnded()) {
            return false;
        }

        final var round = game.currentRound().orElse(null);
        if (round == null) {
            return false;
        }

        if (round.isWaitingForSoledad()) {
            return answerSoledad(gameId, round);
        }
        if (round.isWaitingForTrump()) {
            return nameTrump(gameId, round);
        }
        if (round.isWaitingForTodo()) {
            return answerTodo(gameId, round);
        }
        if (round.isInProgress()) {
            return playCard(gameId, round);
        }
        return false;
    }

    /** The window is open to everyone at once, so the first bot yet to answer does. */
    private boolean answerSoledad(GameId gameId, Round round) {
        final var bot = round.playerOrder().stream()
            .filter(roster::isBot)
            .filter(player -> !round.soledadPasses().contains(player))
            .findFirst()
            .orElse(null);

        if (bot == null) {
            return false;
        }

        pause();

        if (mayGoAlone(round, bot) && strategy.declaresSoledad(view(round, bot))) {
            return soledadUseCase.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(gameId, bot)
            ) instanceof SoledadUseCase.DeclareSoledadResult.Success;
        }

        return soledadUseCase.passSoledad(
            new SoledadUseCase.PassSoledadCommand(gameId, bot)
        ) instanceof SoledadUseCase.PassSoledadResult.Success;
    }

    /** A four-king deal is nobody else's decision to make. */
    private boolean mayGoAlone(Round round, PlayerId player) {
        return round.fourKingHolder().map(player::equals).orElse(true);
    }

    private boolean nameTrump(GameId gameId, Round round) {
        final var chooser = round.trumpChooser();
        if (!roster.isBot(chooser)) {
            return false;
        }

        pause();
        final var suit = strategy.chooseTrump(view(round, chooser));

        return selectTrumpUseCase.selectTrump(
            new SelectTrumpUseCase.SelectTrumpCommand(gameId, chooser, suit)
        ) instanceof SelectTrumpUseCase.SelectTrumpResult.Success;
    }

    private boolean answerTodo(GameId gameId, Round round) {
        final var caller = round.todoCaller().orElse(null);
        if (!roster.isBot(caller)) {
            return false;
        }

        pause();
        final var call = strategy.callsTodo(view(round, caller));

        return todoUseCase.decideTodo(
            new TodoUseCase.TodoCommand(gameId, caller), call
        ) instanceof TodoUseCase.TodoResult.Success;
    }

    private boolean playCard(GameId gameId, Round round) {
        final var player = round.currentPlayer().orElse(null);
        if (!roster.isBot(player)) {
            return false;
        }

        final var legal = moveValidator.legalCards(
            round.getHand(player),
            round.trumpSuit().orElseThrow(),
            ledCard(round),
            new MoveValidator.LeadContext(round.ledSuits(), round.sideDecided())
        );

        if (legal.isEmpty()) {
            log.warn("Bot {} has no legal card in game {}", player.value(), gameId.value());
            return false;
        }

        pause();
        final var card = strategy.chooseCard(view(round, player), legal);

        final var result = playCardUseCase.playCard(
            new PlayCardUseCase.PlayCardCommand(gameId, player, card)
        );

        if (!(result instanceof PlayCardUseCase.PlayCardResult.Success)) {
            log.warn("Bot {} had {} rejected: {}", player.value(), card, result);
            return false;
        }
        return true;
    }

    private Optional<Card> ledCard(Round round) {
        return round.currentBasa()
            .filter(basa -> !basa.cardsPlayed().isEmpty())
            .map(basa -> basa.cardsPlayed().get(0).card());
    }

    private BotStrategy.BotView view(Round round, PlayerId player) {
        return new BotStrategy.BotView(round, player);
    }

    /** A beat before each move, so a human can watch the cards land one at a time. */
    private void pause() {
        if (moveDelay.isZero() || moveDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(moveDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
