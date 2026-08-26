package com.ginebra.game.application;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.domain.event.GameEvent;
import com.ginebra.game.domain.model.RoundMode;
import com.ginebra.game.domain.model.Round;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.game.domain.service.BasaResolver;
import com.ginebra.game.domain.service.CardRankingService;
import com.ginebra.game.domain.service.MoveValidator;
import com.ginebra.game.domain.service.SettlementCalculator;
import com.ginebra.game.port.in.PlayCardUseCase;
import com.ginebra.game.port.in.SelectTrumpUseCase;
import com.ginebra.game.port.in.SoledadUseCase;
import com.ginebra.game.port.in.StartGameUseCase;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.support.LegalMoves;
import com.ginebra.support.RecordingGameEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Soledad driven through the application service with real in-memory adapters,
 * so the declaration, the trump handover and the published event stream are all exercised together.
 *
 * The disabled tests are the acceptance criteria for the Soledad gap recorded in PROGRESS.md;
 * the unit-level counterparts live in
 * {@code com.ginebra.game.domain.model.SoledadRoundRulesTest}.
 */
@DisplayName("Soledad through GameService")
class SoledadGameServiceIntegrationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-01-15T10:00:00Z"),
        ZoneId.of("UTC")
    );
    private static final Suit TRUMP = Suit.COPAS;
    private static final int MAX_CARDS_PER_ROUND = Round.TOTAL_CARDS;

    private InMemoryGameRepository gameRepository;
    private RecordingGameEventPublisher eventPublisher;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameRepository = new InMemoryGameRepository();
        eventPublisher = new RecordingGameEventPublisher();

        final var cardRankingService = new CardRankingService();
        gameService = new GameService(
            gameRepository,
            eventPublisher,
            new MoveValidator(cardRankingService),
            new BasaResolver(cardRankingService),
            FIXED_CLOCK,
            new Random(42)
        );
    }

    @Nested
    @DisplayName("Declaration")
    class Declaration {

        @Test
        void shouldBroadcastDeclarationAndClosedWindowToTheWholeGame() {
            // Arrange
            final var table = startGame();

            // Act
            final var result = gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Assert
            assertThat(result).isInstanceOf(SoledadUseCase.DeclareSoledadResult.Success.class);
            assertThat(eventPublisher.firstEventOfType(GameEvent.SoledadDeclared.class).byPlayer())
                .isEqualTo(table.declarer());
            assertThat(eventPublisher.firstEventOfType(GameEvent.SoledadWindowClosed.class).declared())
                .isTrue();
        }

        @Test
        void shouldRecordDeclarerOnTheRound() {
            // Arrange
            final var table = startGame();

            // Act
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Assert
            assertThat(currentRound(table.gameId()).soledadPlayer()).contains(table.declarer());
            assertThat(currentRound(table.gameId()).isWaitingForTrump()).isTrue();
        }

        @Test
        void shouldRejectPassAfterSomeoneDeclared() {
            // Arrange
            final var table = startGame();
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Act
            final var result = gameService.passSoledad(
                new SoledadUseCase.PassSoledadCommand(table.gameId(), table.starter())
            );

            // Assert
            assertThat(result).isInstanceOf(SoledadUseCase.PassSoledadResult.WindowClosed.class);
        }

        @Test
        void shouldRejectSecondDeclaration() {
            // Arrange
            final var table = startGame();
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Act
            final var result = gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.starter())
            );

            // Assert
            assertThat(result).isInstanceOf(SoledadUseCase.DeclareSoledadResult.WindowClosed.class);
        }

        @Test
        void shouldRejectDuplicatePassFromSamePlayer() {
            // Arrange
            final var table = startGame();
            gameService.passSoledad(
                new SoledadUseCase.PassSoledadCommand(table.gameId(), table.starter())
            );

            // Act
            final var result = gameService.passSoledad(
                new SoledadUseCase.PassSoledadCommand(table.gameId(), table.starter())
            );

            // Assert
            assertThat(result).isInstanceOf(SoledadUseCase.PassSoledadResult.AlreadyPassed.class);
        }

        @Test
        void shouldReportGameNotFoundForUnknownGame() {
            // Act
            final var result = gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(GameId.generate(), PlayerId.generate())
            );

            // Assert
            assertThat(result).isInstanceOf(SoledadUseCase.DeclareSoledadResult.GameNotFound.class);
        }
    }

    @Nested
    @DisplayName("Trump selection after a declaration")
    class TrumpSelection {

        @Test
        void shouldLetTheDeclaringPlayerChooseTrump() {
            // Arrange
            final var table = startGame();
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Act
            final var result = gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(table.gameId(), table.declarer(), TRUMP)
            );

            // Assert
            assertThat(result).isInstanceOf(SelectTrumpUseCase.SelectTrumpResult.Success.class);
            assertThat(currentRound(table.gameId()).trumpSuit()).contains(TRUMP);
        }

        @Test
        void shouldRejectTrumpFromAPlayerWhoDidNotDeclare() {
            // Arrange
            final var table = startGame();
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Act
            final var result = gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(table.gameId(), table.starter(), TRUMP)
            );

            // Assert
            assertThat(result).isInstanceOf(SelectTrumpUseCase.SelectTrumpResult.NotYourTurn.class);
        }

        @Test
        void shouldStartPlayWithTheNormalRotationStarter() {
            // Arrange
            final var table = startGame();
            gameService.declareSoledad(
                new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
            );

            // Act
            gameService.selectTrump(
                new SelectTrumpUseCase.SelectTrumpCommand(table.gameId(), table.declarer(), TRUMP)
            );

            // Assert
            assertThat(currentRound(table.gameId()).currentPlayer()).contains(table.starter());
            assertThat(eventPublisher.firstEventOfType(GameEvent.TrumpSelected.class).currentTurn())
                .isEqualTo(table.starter());
        }
    }

    @Nested
    @DisplayName("Playing out a Soledad round")
    class PlayingOut {

        @Test
        void shouldNeverHandTheSoledadPlayerAPartner() {
            // Arrange
            final var table = declareSoledadAndSelectTrump();

            // Act
            playUntilRoundEnds(table.gameId());

            // Assert
            assertThat(eventPublisher.eventsOfType(GameEvent.SideDecided.class))
                .allSatisfy(event -> assertThat(event.mode()).isEqualTo(RoundMode.SOLEDAD));
        }

        @Test
        void shouldSettleCoinsAtTheSoledadRateAgainstThePosso() {
            // Arrange
            final var table = declareSoledadAndSelectTrump();

            // Act
            final var roundEnded = playUntilRoundEnds(table.gameId());

            // Assert: the declarer settles the Soledad base one way or the other. The
            // dengue can add a coin on top, so the base is a floor, not an equality.
            final var changes = roundEnded.coinChanges();
            assertThat(Math.abs(changes.get(table.declarer())))
                .as("anar a soles settles at 5, not at a flat team rate")
                .isGreaterThanOrEqualTo(SettlementCalculator.BASE_SOLEDAD);

            final var declarerWon = changes.get(table.declarer()) > 0;
            for (final var opponent : table.opponents()) {
                if (declarerWon) {
                    assertThat(changes.get(opponent))
                        .as("a losing opponent pays nothing; the pot covers the win")
                        .isLessThanOrEqualTo(SettlementCalculator.INCREMENT);
                } else {
                    assertThat(changes.get(opponent))
                        .as("opponent %s collects from the posso", opponent)
                        .isGreaterThanOrEqualTo(SettlementCalculator.OPPOSING_SIDE_AWARD);
                }
            }
        }

        @Test
        void shouldRotateToTheSeatRightOfTheNormalStarterInTheNextRound() {
            // Arrange
            final var table = declareSoledadAndSelectTrump();

            // Act
            playUntilRoundEnds(table.gameId());

            // Assert
            final var expected = table.playerToTheRightOfStarter();
            assertThat(currentRound(table.gameId()).playerWhoGoes()).isEqualTo(expected);
        }
    }

    // === Helpers ===

    private Table startGame() {
        final var gameId = GameId.generate();
        final var players = List.of(
            PlayerId.generate(), PlayerId.generate(), PlayerId.generate(),
            PlayerId.generate(), PlayerId.generate()
        );
        gameService.startGame(new StartGameUseCase.StartGameCommand(gameId, players));

        final var round = currentRound(gameId);
        final var starter = round.playerWhoGoes();
        final var declarer = players.stream()
            .filter(player -> !player.equals(starter))
            .findFirst()
            .orElseThrow();

        eventPublisher.clear();
        return new Table(gameId, players, starter, declarer);
    }

    private Table declareSoledadAndSelectTrump() {
        final var table = startGame();
        gameService.declareSoledad(
            new SoledadUseCase.DeclareSoledadCommand(table.gameId(), table.declarer())
        );
        gameService.selectTrump(
            new SelectTrumpUseCase.SelectTrumpCommand(table.gameId(), table.declarer(), TRUMP)
        );
        return table;
    }

    /**
     * Plays legal cards until the round reports it has ended, and returns that event.
     */
    private GameEvent.RoundEnded playUntilRoundEnds(GameId gameId) {
        for (var play = 0; play < MAX_CARDS_PER_ROUND; play++) {
            final var round = currentRound(gameId);
            if (!round.isInProgress()) {
                break;
            }
            final var player = round.currentPlayer().orElseThrow();
            final var result = gameService.playCard(
                new PlayCardUseCase.PlayCardCommand(gameId, player, LegalMoves.forRound(round, player))
            );
            assertThat(result).isInstanceOf(PlayCardUseCase.PlayCardResult.Success.class);

            final var ended = eventPublisher.eventsOfType(GameEvent.RoundEnded.class);
            if (!ended.isEmpty()) {
                return ended.get(0);
            }
        }
        throw new AssertionError("Round did not end after " + MAX_CARDS_PER_ROUND + " card plays");
    }

    private Round currentRound(GameId gameId) {
        return gameRepository.findById(gameId).orElseThrow().currentRound().orElseThrow();
    }

    private record Table(GameId gameId, List<PlayerId> players, PlayerId starter, PlayerId declarer) {

        List<PlayerId> opponents() {
            return players.stream().filter(player -> !player.equals(declarer)).toList();
        }

        PlayerId playerToTheRightOfStarter() {
            return players.get((players.indexOf(starter) + 1) % players.size());
        }
    }
}
