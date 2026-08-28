package com.ginebra.game.application;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.adapter.out.NoOpGameEventPublisher;
import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Game;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.BotStrategy;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.RandomBotStrategy;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BotTurnDriver - taking the turns of the seats nobody is sitting in")
class BotTurnDriverTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("UTC")
    );

    private InMemoryGameRepository gameRepository;
    private GameService gameService;
    private BotRoster roster;
    private MoveValidator moveValidator;
    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(1234);
        gameRepository = new InMemoryGameRepository();
        final var cardRanking = new CardRankingService();
        moveValidator = new MoveValidator(cardRanking);
        gameService = new GameService(
            gameRepository,
            new NoOpGameEventPublisher(),
            moveValidator,
            new BasaResolver(cardRanking),
            FIXED_CLOCK,
            random
        );
        roster = new BotRoster();
    }

    /** Runs the bots here and now, with no pause: tests should not wait on a clock. */
    private BotTurnDriver driverWith(BotStrategy strategy) {
        return new BotTurnDriver(
            gameRepository,
            roster,
            strategy,
            moveValidator,
            gameService,
            gameService,
            gameService,
            gameService,
            gameService,
            Runnable::run,
            Duration.ZERO
        );
    }

    private BotTurnDriver driver() {
        return driverWith(new RandomBotStrategy(random));
    }

    /** Five players; the first {@code botCount} of them have nobody behind them. */
    private List<PlayerId> tableWith(int botCount) {
        final var players = new ArrayList<PlayerId>();
        for (var i = 0; i < Round.PLAYER_COUNT; i++) {
            players.add(PlayerId.generate());
        }
        players.subList(0, botCount).forEach(roster::register);
        return List.copyOf(players);
    }

    private GameId startGame(List<PlayerId> players) {
        final var gameId = GameId.generate();
        final var result = gameService.startGame(
            new StartGameUseCase.StartGameCommand(gameId, players)
        );
        assertThat(result).isInstanceOf(StartGameUseCase.StartGameResult.Success.class);
        return gameId;
    }

    private Game game(GameId gameId) {
        return gameRepository.findById(gameId).orElseThrow();
    }

    private Round round(GameId gameId) {
        return game(gameId).currentRound().orElseThrow();
    }

    @Nested
    @DisplayName("Whose turn it takes")
    class WhoseTurn {

        @Test
        void shouldDoNothingAtATableOfPeople() {
            final var gameId = startGame(tableWith(0));

            final var moves = driver().driveNow(gameId);

            assertThat(moves).isZero();
            assertThat(round(gameId).isWaitingForSoledad()).isTrue();
            assertThat(round(gameId).soledadPasses()).isEmpty();
        }

        @Test
        void shouldAnswerTheSoledadWindowForEveryBotAndThenStop() {
            final var players = tableWith(4);
            final var human = players.get(4);
            final var gameId = startGame(players);

            driver().driveNow(gameId);

            final var round = round(gameId);
            assertThat(round.soledadPasses()).containsExactlyInAnyOrderElementsOf(
                players.subList(0, 4)
            );
            assertThat(round.soledadPasses()).doesNotContain(human);
            assertThat(round.isWaitingForSoledad())
                .as("still waiting - the window is the human's to answer")
                .isTrue();
        }

        @Test
        void shouldNameATrumpWhenTheOneWhoGoesIsABot() {
            // Everyone but the ma is a bot, so the human passing closes the window and
            // leaves the naming of trumps to a bot.
            final var players = tableWith(5);
            final var gameId = startGame(players);
            final var chooser = round(gameId).trumpChooser();
            assertThat(roster.isBot(chooser)).isTrue();

            driver().driveNow(gameId);

            assertThat(round(gameId).trumpSuit()).isPresent();
        }

        @Test
        void shouldStopAsSoonAsTheCardIsTheHumansToPlay() {
            final var players = tableWith(4);
            final var human = players.get(4);
            final var gameId = startGame(players);

            passForThePeople(gameId, players);
            driver().driveNow(gameId);

            final var round = round(gameId);
            assertThat(round.isInProgress()).isTrue();
            assertThat(round.currentPlayer()).contains(human);
        }
    }

    @Nested
    @DisplayName("Playing a round out")
    class PlayingOut {

        @Test
        void shouldCarryFourBotsAndOnePersonThroughAWholeRound() {
            final var players = tableWith(4);
            final var human = players.get(4);
            final var gameId = startGame(players);
            final var driver = driver();

            passForThePeople(gameId, players);

            var humanPlays = 0;
            while (!round(gameId).isComplete() && game(gameId).currentRound().get().roundNumber() == 1) {
                driver.driveNow(gameId);

                final var round = round(gameId);
                if (round.isComplete() || round.roundNumber() != 1) {
                    break;
                }
                if (round.isWaitingForTodo() || round.isWaitingForSoledad()) {
                    // Both are the human's call here; bank the win and pass respectively.
                    answerAsHuman(gameId, round, human);
                    continue;
                }

                assertThat(round.currentPlayer())
                    .as("if the bots have stopped, it is because the human is due")
                    .contains(human);

                playSomethingLegal(gameId, round, human);
                humanPlays++;
                assertThat(humanPlays).isLessThanOrEqualTo(Round.MAX_BASAS);
            }

            assertThat(humanPlays)
                .as("the human was dealt in and had to play")
                .isGreaterThan(0);
        }

        @Test
        void shouldDealAndPlayOnIntoTheNextRound() {
            final var players = tableWith(4);
            final var human = players.get(4);
            final var gameId = startGame(players);
            final var driver = driver();

            passForThePeople(gameId, players);

            // Enough turns to see at least one round settled and the next one dealt.
            for (var turn = 0; turn < 200 && round(gameId).roundNumber() == 1; turn++) {
                driver.driveNow(gameId);
                final var round = round(gameId);
                if (round.roundNumber() != 1 || game(gameId).isEnded()) {
                    break;
                }
                answerAsHuman(gameId, round, human);
            }

            assertThat(round(gameId).roundNumber())
                .as("the settled round was followed by a fresh deal")
                .isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("What it will and will not do")
    class Judgement {

        @Test
        void shouldNeverPlayACardTheRulesRefuse() {
            // The use cases would reject an illegal card, so a round that plays out at all
            // is a round in which every bot move was legal. This pins the reverse: a
            // strategy that returns something illegal is caught rather than swallowed.
            final var players = tableWith(5);
            final var gameId = startGame(players);
            final var cheat = new RandomBotStrategy(random) {
                @Override
                public Card chooseCard(BotView view, List<Card> legalCards) {
                    final var hand = view.hand();
                    return hand.stream()
                        .filter(card -> !legalCards.contains(card))
                        .findFirst()
                        .orElse(legalCards.get(0));
                }
            };

            driverWith(cheat).driveNow(gameId);

            // It got as far as naming trumps, and then stuck: an illegal card is refused
            // by the same validator that judges a person's, and the driver stops rather
            // than hammering at it.
            assertThat(round(gameId).trumpSuit()).isPresent();
            assertThat(round(gameId).isComplete())
                .as("a cheating strategy cannot play the round out")
                .isFalse();
            assertThat(round(gameId).roundNumber()).isEqualTo(1);
        }

        @Test
        void shouldNotGoAloneAndShouldNotCallTodo() {
            final var strategy = new RandomBotStrategy(random);
            final var players = tableWith(5);
            final var gameId = startGame(players);
            final var view = new BotStrategy.BotView(round(gameId), players.get(0));

            assertThat(strategy.declaresSoledad(view)).isFalse();
            assertThat(strategy.callsTodo(view)).isFalse();
        }

        @Test
        void shouldNotDriveTheSameTableTwiceAtOnce() {
            // Two nudges can land together - a person's move and the one that dealt the
            // round. The second must find the table already being played and leave it.
            final var gameId = startGame(tableWith(5));
            final var holder = new BotTurnDriver[1];
            final var reentrantMoves = new ArrayList<Integer>();

            final var reentrant = new RandomBotStrategy(random) {
                @Override
                public Suit chooseTrump(BotView view) {
                    reentrantMoves.add(holder[0].driveNow(gameId));
                    return super.chooseTrump(view);
                }
            };
            holder[0] = driverWith(reentrant);

            holder[0].driveNow(gameId);

            assertThat(reentrantMoves)
                .as("the nudge that arrived mid-drive did nothing")
                .isNotEmpty()
                .containsOnly(0);
        }

        @Test
        void shouldReleaseTheGuardWhenTheDriveIsDone() {
            final var gameId = startGame(tableWith(0));
            final var driver = driver();

            assertThat(driver.driveNow(gameId)).isZero();
            assertThat(driver.driveNow(gameId)).isZero();
        }
    }

    // --- the human's side of the table ---------------------------------------------

    private void passForThePeople(GameId gameId, List<PlayerId> players) {
        for (final var player : players) {
            if (!roster.isBot(player)) {
                gameService.passSoledad(
                    new com.ginebra.game.port.in.SoledadUseCase.PassSoledadCommand(gameId, player)
                );
            }
        }
    }

    /** Whatever the round is asking the human for, give it the least eventful answer. */
    private void answerAsHuman(GameId gameId, Round round, PlayerId human) {
        if (round.isWaitingForSoledad() && !round.soledadPasses().contains(human)) {
            gameService.passSoledad(
                new com.ginebra.game.port.in.SoledadUseCase.PassSoledadCommand(gameId, human)
            );
        } else if (round.isWaitingForTrump() && round.trumpChooser().equals(human)) {
            gameService.selectTrump(
                new com.ginebra.game.port.in.SelectTrumpUseCase.SelectTrumpCommand(
                    gameId, human, Suit.OROS
                )
            );
        } else if (round.isWaitingForTodo() && round.todoCaller().map(human::equals).orElse(false)) {
            gameService.decideTodo(
                new com.ginebra.game.port.in.TodoUseCase.TodoCommand(gameId, human), false
            );
        } else if (round.isInProgress() && round.currentPlayer().map(human::equals).orElse(false)) {
            playSomethingLegal(gameId, round, human);
        }
    }

    private void playSomethingLegal(GameId gameId, Round round, PlayerId human) {
        final var legal = moveValidator.legalCards(
            round.getHand(human),
            round.trumpSuit().orElseThrow(),
            round.currentBasa()
                .filter(basa -> !basa.cardsPlayed().isEmpty())
                .map(basa -> basa.cardsPlayed().get(0).card()),
            new MoveValidator.LeadContext(round.ledSuits(), round.sideDecided())
        );
        assertThat(legal).isNotEmpty();

        final var result = gameService.playCard(
            new PlayCardUseCase.PlayCardCommand(gameId, human, legal.get(0))
        );
        assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.Success.class);
    }
}
