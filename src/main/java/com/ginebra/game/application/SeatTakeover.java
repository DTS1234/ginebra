package com.ginebra.game.application;

import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.port.in.SeatTakeoverUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.game.port.out.GameRepository;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts a bot in the seat of someone who has gone, and takes it out again when they come
 * back.
 *
 * Nothing here touches the round: the game does not know or care that the hand it is
 * dealing with is being played by a bot, which is the whole reason this is three lines of
 * roster juggling rather than a mode the engine has to carry.
 *
 * Which seats were taken over is kept separately from the roster because the roster also
 * holds the bots that were dealt in as bots, and those must never be handed to anyone.
 */
@Service
public class SeatTakeover implements SeatTakeoverUseCase {

    private final GameRepository gameRepository;
    private final BotRoster roster;
    private final GameEventPublisher eventPublisher;
    private final BotTurnDriver botTurnDriver;

    /** Seats that belong to a person who is away. */
    private final Set<PlayerId> borrowed = ConcurrentHashMap.newKeySet();

    public SeatTakeover(
        GameRepository gameRepository,
        BotRoster roster,
        GameEventPublisher eventPublisher,
        BotTurnDriver botTurnDriver
    ) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository must not be null");
        this.roster = Objects.requireNonNull(roster, "roster must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.botTurnDriver = Objects.requireNonNull(botTurnDriver, "botTurnDriver must not be null");
    }

    @Override
    public boolean takeOverSeat(GameId gameId, PlayerId playerId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");

        final var game = gameRepository.findById(gameId).orElse(null);
        if (game == null || game.isEnded() || !game.players().contains(playerId)) {
            return false;
        }

        // Already a bot: either this seat has been taken over once already, or nobody was
        // ever sitting in it. Either way there is nothing to take.
        if (roster.isBot(playerId)) {
            return false;
        }

        borrowed.add(playerId);
        roster.register(playerId);
        eventPublisher.publishToGame(gameId, new GameEvent.SeatTakenOver(playerId));

        // The seat is very likely the one the table is waiting on, and a bot's turn does
        // not take itself.
        botTurnDriver.drive(gameId);
        return true;
    }

    @Override
    public boolean returnSeat(GameId gameId, PlayerId playerId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");

        if (!borrowed.remove(playerId)) {
            return false;
        }

        roster.release(playerId);
        eventPublisher.publishToGame(gameId, new GameEvent.SeatReturned(playerId));
        return true;
    }
}
