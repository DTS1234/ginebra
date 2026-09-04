package com.ginebra.game.application;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.RandomBotStrategy;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.game.port.out.GameEventPublisher;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("A seat whose player has gone")
class SeatTakeoverTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("UTC")
    );

    private InMemoryGameRepository gameRepository;
    private BotRoster roster;
    private CapturingPublisher publisher;
    private SeatTakeover seatTakeover;
    private List<PlayerId> players;
    private GameId gameId;

    @BeforeEach
    void setUp() {
        final var random = new Random(7);
        final var cardRanking = new CardRankingService();
        final var moveValidator = new MoveValidator(cardRanking);

        gameRepository = new InMemoryGameRepository();
        roster = new BotRoster();
        publisher = new CapturingPublisher();

        final var gameService = new GameService(
            gameRepository,
            publisher,
            moveValidator,
            new BasaResolver(cardRanking),
            FIXED_CLOCK,
            random
        );

        // Runs the bots here and now: a test should not have to wait for a thread to get
        // round to proving the seat is being played.
        final var botTurnDriver = new BotTurnDriver(
            gameRepository,
            roster,
            new RandomBotStrategy(random),
            moveValidator,
            gameService,
            gameService,
            gameService,
            gameService,
            gameService,
            Runnable::run,
            Duration.ZERO
        );

        seatTakeover = new SeatTakeover(gameRepository, roster, publisher, botTurnDriver);

        players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        gameId = GameId.generate();
        gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));
        publisher.events.clear();
    }

    @Test
    void shouldPutABotInIt() {
        final var gone = players.get(0);

        assertThat(seatTakeover.takeOverSeat(gameId, gone)).isTrue();

        assertThat(roster.isBot(gone)).isTrue();
        assertThat(publisher.events)
            .as("the other four are owed an explanation for who is playing that hand")
            .startsWith(new GameEvent.SeatTakenOver(gone));
    }

    @Test
    void shouldGetOnWithTheHandRatherThanWaitForSomethingElseToNudgeIt() {
        // The seat is very likely the one the table is stuck on, and a bot's turn does
        // not take itself: handing the seat over has to start it playing.
        seatTakeover.takeOverSeat(gameId, players.get(0));

        final var round = gameRepository.findById(gameId).orElseThrow()
            .currentRound().orElseThrow();
        assertThat(round.soledadPasses())
            .as("the bot answered the window it was handed")
            .contains(players.get(0));
    }

    @Test
    void shouldGiveItBackWhenTheyReturn() {
        final var gone = players.get(0);
        seatTakeover.takeOverSeat(gameId, gone);
        publisher.events.clear();

        assertThat(seatTakeover.returnSeat(gameId, gone)).isTrue();

        assertThat(roster.isBot(gone)).isFalse();
        assertThat(publisher.events).containsExactly(new GameEvent.SeatReturned(gone));
    }

    @Test
    void shouldNotBeTakenTwice() {
        final var gone = players.get(0);
        seatTakeover.takeOverSeat(gameId, gone);
        publisher.events.clear();

        assertThat(seatTakeover.takeOverSeat(gameId, gone)).isFalse();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void shouldNeverHandASeatThatWasABotsAllAlongToAnybody() {
        // A bot that was dealt in as a bot has nobody to give its seat back to. Releasing
        // it would leave a seat in the game that nothing will ever play.
        final var bot = players.get(1);
        roster.register(bot);

        assertThat(seatTakeover.returnSeat(gameId, bot)).isFalse();
        assertThat(roster.isBot(bot))
            .as("still a bot, still played")
            .isTrue();
    }

    @Test
    void shouldIgnoreSomebodyWhoIsNotAtThisTable() {
        final var stranger = PlayerId.generate();

        assertThat(seatTakeover.takeOverSeat(gameId, stranger)).isFalse();
        assertThat(roster.isBot(stranger)).isFalse();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void shouldIgnoreAGameThatIsNoLongerThere() {
        assertThat(seatTakeover.takeOverSeat(GameId.generate(), players.get(0))).isFalse();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void shouldNotHaveToBeReturnedToBeIgnoredLater() {
        assertThat(seatTakeover.returnSeat(gameId, players.get(0)))
            .as("nobody took this seat; there is nothing to hand back")
            .isFalse();
    }

    private static class CapturingPublisher implements GameEventPublisher {

        final List<GameEvent> events = new ArrayList<>();

        @Override
        public void publishToGame(GameId gameId, GameEvent event) {
            events.add(event);
        }

        @Override
        public void publishToPlayer(GameId gameId, PlayerId playerId, GameEvent event) {
        }
    }
}
