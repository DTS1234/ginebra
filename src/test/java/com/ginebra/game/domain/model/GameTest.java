package com.ginebra.game.domain.model;

import com.ginebra.game.domain.service.SettlementCalculator;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.domain.GameId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    private static final long TEST_SEED = 42L;

    // === Helper Methods ===

    private List<PlayerId> createPlayers() {
        return List.of(
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate(),
            PlayerId.generate()
        );
    }

    private Game createGame(List<PlayerId> players) {
        return Game.start(GameId.generate(), players, new Random(TEST_SEED), Instant.now());
    }

    private Game passAllSoledad(Game game) {
        final var round = game.currentRound().orElseThrow();
        for (final var player : round.playerOrder()) {
            game = game.passSoledad(player);
        }
        return game;
    }

    private Game createGameWithTrump(List<PlayerId> players, Suit trump) {
        return passAllSoledad(createGame(players)).selectTrump(trump);
    }

    /**
     * Plays 5 cards into the current basa using the first card from each player's hand.
     */
    private Game playFullBasa(Game game) {
        for (var i = 0; i < 5; i++) {
            final var round = game.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);
            game = game.playCard(currentPlayer, card, Instant.now());
        }
        return game;
    }

    /**
     * Plays a full basa and completes it with the given winner.
     */
    private Game playAndCompleteBasa(Game game, PlayerId winner) {
        game = playFullBasa(game);
        return game.completeBasa(winner);
    }

    /**
     * Creates a game, selects trump, sets teams, and plays basas until one team wins.
     * Team of two: players[0] + players[1]. Winner: specified by winningTeam.
     */
    private Game playRoundToCompletion(
        Game game,
        List<PlayerId> players,
        Suit trump,
        PlayerId basaWinner,
        int basasToWin
    ) {
        game = passAllSoledad(game).selectTrump(trump);

        final var teams = Teams.of(
            game.currentRound().orElseThrow().playerWhoGoes(),
            players.get(findTeammate(players, game.currentRound().orElseThrow().playerWhoGoes())),
            new HashSet<>(players)
        );
        game = game.setTeams(teams);

        final var goingSide = game.currentRound().orElseThrow().goingSide();
        if (goingSide.contains(basaWinner)) {
            game = playAndCompleteBasa(game, opponentOf(game, players));
        }
        for (var i = 0; i < basasToWin; i++) {
            game = playAndCompleteBasa(game, basaWinner);
        }
        return game;
    }

    /** Any player on the side opposing the one that goes. */
    private PlayerId opponentOf(Game game, List<PlayerId> players) {
        final var going = game.currentRound().orElseThrow().goingSide();
        return players.stream().filter(p -> !going.contains(p)).findFirst().orElseThrow();
    }

    /**
     * Sets up a 2-v-3 side and plays the round out with the going side taking five,
     * after conceding one so that "todo" is off the table.
     */
    private Game playGoingSideWin(Game game, List<PlayerId> players) {
        final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();
        final var teammate = players.stream()
            .filter(p -> !p.equals(playerWhoGoes))
            .findFirst().orElseThrow();
        game = game.setTeams(Teams.of(playerWhoGoes, teammate, new HashSet<>(players)));

        game = playAndCompleteBasa(game, opponentOf(game, players));
        for (var i = 0; i < 5; i++) {
            game = playAndCompleteBasa(game, playerWhoGoes);
        }
        return game;
    }

    /**
     * Sets up a 2-v-3 side and has the opposing side block with its four basas.
     */
    private Game playGoingSideFailure(Game game, List<PlayerId> players) {
        final var playerWhoGoes = game.currentRound().orElseThrow().playerWhoGoes();
        final var teammate = players.stream()
            .filter(p -> !p.equals(playerWhoGoes))
            .findFirst().orElseThrow();
        game = game.setTeams(Teams.of(playerWhoGoes, teammate, new HashSet<>(players)));

        final var opponent = opponentOf(game, players);
        for (var i = 0; i < Round.BASAS_TO_WIN; i++) {
            game = playAndCompleteBasa(game, opponent);
        }
        return game;
    }

    /** Coins never leave the table: what the players hold plus the pot is constant. */
    private void assertTableIsConserved(Game game, List<PlayerId> players) {
        final var held = players.stream().mapToInt(game::getCoins).sum();
        assertThat(held + game.posso())
            .as("coins held plus the posso")
            .isEqualTo(Game.INITIAL_COINS * 5);
    }

    /**
     * Finds a teammate index for the given playerWhoGoes (anyone except themselves).
     */
    private int findTeammate(List<PlayerId> players, PlayerId playerWhoGoes) {
        for (var i = 0; i < players.size(); i++) {
            if (!players.get(i).equals(playerWhoGoes)) {
                return i;
            }
        }
        throw new IllegalStateException("No teammate found");
    }

    @Nested
    class Start {

        @Test
        void shouldCreateGameInProgressWithFirstRound() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(game.isInProgress()).isTrue();
            assertThat(game.currentRound()).isPresent();
            assertThat(game.currentRound().get().roundNumber()).isEqualTo(1);
        }

        @Test
        void shouldSeatFivePlayersWhoHaveEachAntedIntoThePosso() {
            final var players = createPlayers();
            final var game = createGame(players);

            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(Game.INITIAL_COINS - Game.ANTE);
            }
            assertThat(game.coinBalances()).hasSize(5);
            assertThat(game.posso()).isEqualTo(Game.ANTE * 5);
        }

        @Test
        void shouldDetermineEspadillaHolderAsFirstRoundStarter() {
            final var players = createPlayers();
            final var game = createGame(players);

            final var round = game.currentRound().orElseThrow();
            final var starter = round.playerWhoGoes();

            // Verify the starter actually holds the Espadilla
            final var starterHand = round.getHand(starter);
            assertThat(starterHand).anyMatch(Card::isEspadilla);
        }

        @Test
        void shouldDealEightCardsToEachPlayerInFirstRound() {
            final var players = createPlayers();
            final var game = createGame(players);

            final var round = game.currentRound().orElseThrow();
            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(8);
            }
        }

        @Test
        void shouldCreateRoundInWaitingForTrumpStatus() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.currentRound().get().isWaitingForSoledad()).isTrue();
        }

        @Test
        void shouldSetCorrectGameId() {
            final var gameId = GameId.generate();
            final var players = createPlayers();
            final var game = Game.start(gameId, players, new Random(TEST_SEED), Instant.now());

            assertThat(game.gameId()).isEqualTo(gameId);
        }

        @Test
        void shouldInitializeWithEmptyCompletedRounds() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.completedRounds()).isEmpty();
        }

        @Test
        void shouldSetCorrectPlayers() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.players()).containsExactlyElementsOf(players);
        }

        @Test
        void shouldRejectNullGameId() {
            final var players = createPlayers();

            assertThatThrownBy(() -> Game.start(null, players, new Random(TEST_SEED), Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gameId must not be null");
        }

        @Test
        void shouldRejectNullPlayers() {
            assertThatThrownBy(() -> Game.start(GameId.generate(), null, new Random(TEST_SEED), Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("players must not be null");
        }

        @Test
        void shouldRejectNullRandom() {
            final var players = createPlayers();

            assertThatThrownBy(() -> Game.start(GameId.generate(), players, null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("random must not be null");
        }

        @Test
        void shouldRejectWrongPlayerCount() {
            final var players = List.of(PlayerId.generate(), PlayerId.generate(), PlayerId.generate());

            assertThatThrownBy(() -> Game.start(GameId.generate(), players, new Random(TEST_SEED), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Game requires exactly 5 players");
        }

        @Test
        void shouldRejectDuplicatePlayers() {
            final var player = PlayerId.generate();
            final var players = List.of(player, player, PlayerId.generate(), PlayerId.generate(), PlayerId.generate());

            assertThatThrownBy(() -> Game.start(GameId.generate(), players, new Random(TEST_SEED), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate players");
        }

        @Test
        void shouldProduceDeterministicResultWithSameRandomSeed() {
            final var players = createPlayers();
            final var game1 = Game.start(GameId.generate(), players, new Random(TEST_SEED), Instant.now());
            final var game2 = Game.start(GameId.generate(), players, new Random(TEST_SEED), Instant.now());

            final var round1 = game1.currentRound().orElseThrow();
            final var round2 = game2.currentRound().orElseThrow();

            assertThat(round1.playerWhoGoes()).isEqualTo(round2.playerWhoGoes());
            for (final var player : players) {
                assertThat(round1.getHand(player)).isEqualTo(round2.getHand(player));
            }
        }
    }

    @Nested
    class SelectTrump {

        @Test
        void shouldDelegateToCurrentRoundWithTrump() {
            final var players = createPlayers();
            final var game = passAllSoledad(createGame(players)).selectTrump(Suit.COPAS);

            assertThat(game.currentRound().get().trumpSuit()).contains(Suit.COPAS);
        }

        @Test
        void shouldTransitionRoundToInProgress() {
            final var players = createPlayers();
            final var game = passAllSoledad(createGame(players)).selectTrump(Suit.COPAS);

            assertThat(game.currentRound().get().isInProgress()).isTrue();
        }

        @Test
        void shouldRejectWhenGameIsEnded() {
            final var players = createPlayers();
            final var game = createEndedGame(players);

            assertThatThrownBy(() -> game.selectTrump(Suit.COPAS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Game is not in progress");
        }

        @Test
        void shouldRejectNullTrump() {
            final var players = createPlayers();
            final var game = passAllSoledad(createGame(players));

            assertThatThrownBy(() -> game.selectTrump(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnNewGameInstance() {
            final var players = createPlayers();
            final var original = passAllSoledad(createGame(players));
            final var updated = original.selectTrump(Suit.COPAS);

            assertThat(updated).isNotSameAs(original);
            assertThat(original.currentRound().get().trumpSuit()).isEmpty();
        }
    }

    @Nested
    class PlayCard {

        @Test
        void shouldDelegateToCurrentRound() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);

            final var round = game.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);

            game = game.playCard(currentPlayer, card, Instant.now());

            assertThat(game.currentRound().get().currentBasa().get().cardCount()).isEqualTo(1);
        }

        @Test
        void shouldRemoveCardFromPlayerHand() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);

            final var round = game.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);

            game = game.playCard(currentPlayer, card, Instant.now());

            assertThat(game.currentRound().get().getHand(currentPlayer)).hasSize(7);
            assertThat(game.currentRound().get().getHand(currentPlayer)).doesNotContain(card);
        }

        @Test
        void shouldRejectWhenGameIsEnded() {
            final var players = createPlayers();
            final var game = createEndedGame(players);
            final var card = new Card(Suit.COPAS, Rank.REY);

            assertThatThrownBy(() -> game.playCard(players.get(0), card, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Game is not in progress");
        }

        @Test
        void shouldRejectNullPlayerId() {
            final var players = createPlayers();
            final var game = createGameWithTrump(players, Suit.COPAS);
            final var card = game.currentRound().get().getHand(players.get(0)).get(0);

            assertThatThrownBy(() -> game.playCard(null, card, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullCard() {
            final var players = createPlayers();
            final var game = createGameWithTrump(players, Suit.COPAS);
            final var currentPlayer = game.currentRound().get().currentPlayer().orElseThrow();

            assertThatThrownBy(() -> game.playCard(currentPlayer, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnNewGameInstance() {
            final var players = createPlayers();
            final var original = createGameWithTrump(players, Suit.COPAS);
            final var round = original.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);

            final var updated = original.playCard(currentPlayer, card, Instant.now());

            assertThat(updated).isNotSameAs(original);
            assertThat(original.currentRound().get().currentBasa().get().cardCount()).isZero();
        }
    }

    @Nested
    class SetTeams {

        @Test
        void shouldDelegateToCurrentRound() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();

            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            assertThat(game.currentRound().get().teams()).isPresent();
            assertThat(game.currentRound().get().teams().get().teamOfTwo())
                .containsExactlyInAnyOrder(playerWhoGoes, teammate);
        }

        @Test
        void shouldRejectWhenGameIsEnded() {
            final var players = createPlayers();
            final var game = createEndedGame(players);
            final var teams = new Teams(
                Set.of(players.get(0), players.get(1)),
                Set.of(players.get(2), players.get(3), players.get(4))
            );

            assertThatThrownBy(() -> game.setTeams(teams))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Game is not in progress");
        }

        @Test
        void shouldRejectNullTeams() {
            final var players = createPlayers();
            final var game = createGameWithTrump(players, Suit.COPAS);

            assertThatThrownBy(() -> game.setTeams(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnNewGameInstance() {
            final var players = createPlayers();
            final var original = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = original.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));

            final var updated = original.setTeams(teams);

            assertThat(updated).isNotSameAs(original);
            assertThat(original.currentRound().get().teams()).isEmpty();
        }
    }

    @Nested
    class CompleteBasa {

        @Test
        void shouldCompleteBasaWithoutEndingRound() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            game = playFullBasa(game);

            game = game.completeBasa(players.get(0));

            assertThat(game.isInProgress()).isTrue();
            assertThat(game.currentRound().get().completedBasas()).hasSize(1);
            assertThat(game.isRoundComplete()).isFalse();
        }

        @Test
        void shouldEndRoundWhenTheGoingSideMakesFiveBasas() {
            final var players = createPlayers();
            var game = playGoingSideWin(createGameWithTrump(players, Suit.COPAS), players);

            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.currentRound().get().result()).isPresent();
            assertThat(game.currentRound().get().result().get())
                .isInstanceOf(RoundResult.GoingSideWon.class);
        }

        @Test
        void shouldEndRoundWhenTheOpposingSideReachesFiveBasas() {
            final var players = createPlayers();
            var game = playGoingSideFailure(createGameWithTrump(players, Suit.COPAS), players);

            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.currentRound().get().result().get())
                .isInstanceOf(RoundResult.GoingSideFailed.class);
            assertThat(game.currentRound().get().completedBasas())
                .as("the hand runs until a side has five")
                .hasSize(Round.BASAS_TO_WIN);
        }

        @Test
        void shouldPayTheGoingSideOutOfThePossoOnAWin() {
            final var players = createPlayers();
            final var before = createGameWithTrump(players, Suit.COPAS);
            final var goingSide = playGoingSideWin(before, players)
                .currentRound().orElseThrow().goingSide();
            final var game = playGoingSideWin(before, players);

            // The pot funds the win: every winner is up, and the posso is down by at least
            // the base rate for each of them.
            for (final var winner : goingSide) {
                assertThat(game.getCoins(winner)).isGreaterThan(before.getCoins(winner));
            }
            assertThat(game.posso()).isLessThan(before.posso());
            assertTableIsConserved(game, players);
        }

        @Test
        void shouldTakeFromTheGoingSideAndPayTheOpponentsOnAFailure() {
            final var players = createPlayers();
            final var before = createGameWithTrump(players, Suit.COPAS);
            final var game = playGoingSideFailure(before, players);
            final var round = game.currentRound().orElseThrow();

            for (final var loser : round.goingSide()) {
                assertThat(game.getCoins(loser)).isLessThan(before.getCoins(loser));
            }
            assertTableIsConserved(game, players);
        }

        @Test
        void shouldNotEndGameWhenAllPlayersHavePositiveCoins() {
            final var players = createPlayers();
            final var game = playGoingSideWin(createGameWithTrump(players, Suit.COPAS), players);

            assertThat(game.isInProgress()).isTrue();
            assertThat(game.isEnded()).isFalse();
        }

        @Test
        void shouldRejectWhenGameIsEnded() {
            final var players = createPlayers();
            final var game = createEndedGame(players);

            assertThatThrownBy(() -> game.completeBasa(players.get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Game is not in progress");
        }

        @Test
        void shouldRejectNullWinnerId() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            game = playFullBasa(game);
            final var fullGame = game;

            assertThatThrownBy(() -> fullGame.completeBasa(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StartNextRound {

        @Test
        void shouldStartNewRoundWithNextRoundNumber() {
            final var players = createPlayers();
            var game = completeOneRound(players);

            game = game.startNextRound(new Random(123L), Instant.now());

            assertThat(game.currentRound().get().roundNumber()).isEqualTo(2);
        }

        @Test
        void shouldRotateStarterToRightOfPreviousRoundStarter() {
            final var players = createPlayers();
            var game = completeOneRound(players);
            final var previousStarter = game.currentRound().get().playerWhoGoes();
            final var previousStarterIndex = players.indexOf(previousStarter);
            final var expectedNextStarter = players.get((previousStarterIndex + 1) % 5);

            game = game.startNextRound(new Random(123L), Instant.now());

            assertThat(game.currentRound().get().playerWhoGoes()).isEqualTo(expectedNextStarter);
        }

        @Test
        void shouldDealNewCardsForNextRound() {
            final var players = createPlayers();
            var game = completeOneRound(players);

            game = game.startNextRound(new Random(123L), Instant.now());

            final var round = game.currentRound().orElseThrow();
            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(8);
            }
        }

        @Test
        void shouldMoveCompletedRoundToHistory() {
            final var players = createPlayers();
            var game = completeOneRound(players);
            assertThat(game.completedRounds()).isEmpty();

            game = game.startNextRound(new Random(123L), Instant.now());

            assertThat(game.completedRounds()).hasSize(1);
            assertThat(game.completedRounds().get(0).roundNumber()).isEqualTo(1);
        }

        @Test
        void shouldRejectWhenCurrentRoundNotComplete() {
            final var players = createPlayers();
            final var game = createGameWithTrump(players, Suit.COPAS);

            assertThatThrownBy(() -> game.startNextRound(new Random(123L), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current round is not complete");
        }

        @Test
        void shouldRejectWhenGameIsEnded() {
            final var players = createPlayers();
            final var game = createEndedGame(players);

            assertThatThrownBy(() -> game.startNextRound(new Random(123L), Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Game is not in progress");
        }

        @Test
        void shouldRejectNullRandom() {
            final var players = createPlayers();
            final var game = completeOneRound(players);

            assertThatThrownBy(() -> game.startNextRound(null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("random must not be null");
        }

        @Test
        void shouldPreserveCoinBalancesFromPreviousRound() {
            final var players = createPlayers();
            var game = completeOneRound(players);
            final var coinsBeforeNextRound = new HashMap<>(game.coinBalances());

            game = game.startNextRound(new Random(123L), Instant.now());

            assertThat(game.coinBalances()).isEqualTo(coinsBeforeNextRound);
        }
    }

    /**
     * The posso, rather than the old player-to-player transfers. The exact ladder lives in
     * SettlementCalculatorTest; these pin how the aggregate moves coins around it.
     */
    @Nested
    class Posso {

        @Test
        void shouldFormThePossoFromEqualAntes() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.posso()).isEqualTo(Game.ANTE * 5);
            assertTableIsConserved(game, players);
        }

        @Test
        void shouldConserveTheTableAcrossAWin() {
            final var players = createPlayers();
            final var game = playGoingSideWin(createGameWithTrump(players, Suit.COPAS), players);

            assertTableIsConserved(game, players);
        }

        @Test
        void shouldConserveTheTableAcrossAFailure() {
            final var players = createPlayers();
            final var game = playGoingSideFailure(createGameWithTrump(players, Suit.COPAS), players);

            assertTableIsConserved(game, players);
        }

        @Test
        void shouldConserveTheTableAcrossManyRounds() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            var seed = 500L;

            for (var i = 0; i < 6 && game.isInProgress(); i++) {
                game = i % 2 == 0
                    ? playGoingSideWin(game, players)
                    : playGoingSideFailure(game, players);
                assertTableIsConserved(game, players);

                if (game.isInProgress()) {
                    game = game.startNextRound(new Random(seed++), Instant.now());
                    if (game.currentRound().orElseThrow().isWaitingForSoledad()) {
                        game = passAllSoledad(game).selectTrump(Suit.COPAS);
                    }
                }
            }
        }

        @Test
        void shouldTopThePossoUpInEqualPartsWhenItCannotCoverAPayout() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            var seed = 700L;
            var toppedUp = false;

            // The pot only ever drains on a going-side win, so keep paying them out.
            for (var i = 0; i < 12 && game.isInProgress() && !toppedUp; i++) {
                final var possoBefore = game.posso();
                game = playGoingSideWin(game, players);
                toppedUp = game.posso() > possoBefore;

                assertTableIsConserved(game, players);
                if (game.isInProgress()) {
                    game = game.startNextRound(new Random(seed++), Instant.now());
                    if (game.currentRound().orElseThrow().isWaitingForSoledad()) {
                        game = passAllSoledad(game).selectTrump(Suit.COPAS);
                    }
                }
            }

            assertThat(toppedUp)
                .as("a pot that cannot cover the payout is renewed in equal parts")
                .isTrue();
        }
    }

    @Nested
    class QueryMethods {

        @Test
        void shouldReturnCorrectGameId() {
            final var gameId = GameId.generate();
            final var players = createPlayers();
            final var game = Game.start(gameId, players, new Random(TEST_SEED), Instant.now());

            assertThat(game.gameId()).isEqualTo(gameId);
        }

        @Test
        void shouldReturnCorrectPlayers() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.players()).containsExactlyElementsOf(players);
        }

        @Test
        void shouldReturnCoinBalanceForPlayer() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.getCoins(players.get(0))).isEqualTo(Game.INITIAL_COINS - Game.ANTE);
        }

        @Test
        void getCoinsThrowsForUnknownPlayer() {
            final var players = createPlayers();
            final var game = createGame(players);
            final var unknown = PlayerId.generate();

            assertThatThrownBy(() -> game.getCoins(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player not in game");
        }

        @Test
        void shouldReturnCurrentRound() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.currentRound()).isPresent();
            assertThat(game.currentRound().get().roundNumber()).isEqualTo(1);
        }

        @Test
        void shouldReturnCompletedRounds() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.completedRounds()).isEmpty();
        }

        @Test
        void shouldReturnCorrectRoundNumber() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.roundNumber()).isEqualTo(1);
        }

        @Test
        void isRoundCompleteShouldReturnFalseWhenRoundInProgress() {
            final var players = createPlayers();
            final var game = createGameWithTrump(players, Suit.COPAS);

            assertThat(game.isRoundComplete()).isFalse();
        }

        @Test
        void statusConvenienceMethods() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThat(game.isInProgress()).isTrue();
            assertThat(game.isEnded()).isFalse();
        }
    }

    @Nested
    class Immutability {

        @Test
        void selectTrumpShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = passAllSoledad(createGame(players));
            final var updated = original.selectTrump(Suit.COPAS);

            assertThat(updated).isNotSameAs(original);
            assertThat(original.currentRound().get().trumpSuit()).isEmpty();
        }

        @Test
        void playersShouldBeUnmodifiable() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThatThrownBy(() -> game.players().add(PlayerId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void completedRoundsShouldBeUnmodifiable() {
            final var players = createPlayers();
            var game = completeOneRound(players);
            game = game.startNextRound(new Random(123L), Instant.now());

            final var completedRounds = game.completedRounds();
            assertThatThrownBy(() -> completedRounds.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void coinBalancesShouldBeUnmodifiable() {
            final var players = createPlayers();
            final var game = createGame(players);

            assertThatThrownBy(() -> game.coinBalances().put(PlayerId.generate(), 100))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void playCardShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = createGameWithTrump(players, Suit.COPAS);
            final var round = original.currentRound().orElseThrow();
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);

            final var updated = original.playCard(currentPlayer, card, Instant.now());

            assertThat(updated).isNotSameAs(original);
        }

        @Test
        void completeBasaShouldReturnNewInstance() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            game = playFullBasa(game);
            final var before = game;

            final var after = game.completeBasa(players.get(0));

            assertThat(after).isNotSameAs(before);
            assertThat(before.currentRound().get().completedBasas()).isEmpty();
        }

        @Test
        void setTeamsShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = original.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));

            final var updated = original.setTeams(teams);

            assertThat(updated).isNotSameAs(original);
            assertThat(original.currentRound().get().teams()).isEmpty();
        }
    }

    @Nested
    class FullGameScenarios {

        @Test
        void shouldPlayTwoRoundsWithRoundRotation() {
            final var players = createPlayers();
            var game = createGame(players);

            // Round 1: play to completion
            game = passAllSoledad(game).selectTrump(Suit.COPAS);
            game = playGoingSideFailure(game, players);
            assertThat(game.isRoundComplete()).isTrue();

            final var round1Starter = game.currentRound().get().playerWhoGoes();
            final var round1StarterIndex = players.indexOf(round1Starter);
            final var expectedRound2Starter = players.get((round1StarterIndex + 1) % 5);

            // Start round 2
            game = game.startNextRound(new Random(99L), Instant.now());
            assertThat(game.currentRound().get().roundNumber()).isEqualTo(2);
            assertThat(game.currentRound().get().playerWhoGoes()).isEqualTo(expectedRound2Starter);
            assertThat(game.completedRounds()).hasSize(1);

            // Round 2: play to completion
            game = passAllSoledad(game).selectTrump(Suit.OROS);
            game = playGoingSideFailure(game, players);
            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.roundNumber()).isEqualTo(2);
        }

        @Test
        void shouldCarryCoinBalancesAcrossMultipleRounds() {
            final var players = createPlayers();
            var game = passAllSoledad(createGame(players)).selectTrump(Suit.COPAS);

            game = playGoingSideFailure(game, players);
            final var afterRoundOne = new HashMap<>(game.coinBalances());
            assertTableIsConserved(game, players);

            game = game.startNextRound(new Random(99L), Instant.now());
            assertThat(game.coinBalances())
                .as("a new deal moves no coins on its own")
                .isEqualTo(afterRoundOne);

            game = passAllSoledad(game).selectTrump(Suit.OROS);
            game = playGoingSideFailure(game, players);
            assertTableIsConserved(game, players);
        }

        @Test
        void shouldHandleFullRoundWithTeamWin() {
            final var players = createPlayers();
            var game = createGame(players);

            // Select trump
            game = passAllSoledad(game).selectTrump(Suit.COPAS);

            // Set teams
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            // The opposing side takes one, then the going side takes five
            game = playAndCompleteBasa(game, opponentOf(game, players));
            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            // Verify round is complete
            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.currentRound().get().result().get())
                .isInstanceOf(RoundResult.GoingSideWon.class);

            // Verify the going side collected from the posso
            assertThat(game.getCoins(playerWhoGoes))
                .isGreaterThanOrEqualTo(Game.INITIAL_COINS - Game.ANTE + SettlementCalculator.BASE_HELPED);
            assertThat(game.getCoins(teammate))
                .isGreaterThanOrEqualTo(Game.INITIAL_COINS - Game.ANTE + SettlementCalculator.BASE_HELPED);
            assertTableIsConserved(game, players);

            // Game should still be in progress
            assertThat(game.isInProgress()).isTrue();
        }
    }

    // === Helpers for creating specific game states ===

    /**
     * Creates a game where one round is complete (draw, no teams).
     */
    private Game completeOneRound(List<PlayerId> players) {
        return playGoingSideFailure(createGameWithTrump(players, Suit.COPAS), players);
    }

    /**
     * Plays rounds until a player can no longer cover what they owe and the game ends.
     * The same pair goes and is blocked every round, so their stack drains fastest.
     */
    private Game createEndedGame(List<PlayerId> players) {
        var game = createGame(players);
        var seed = 100L;

        for (var round = 0; round < 40; round++) {
            if (game.isEnded()) {
                return game;
            }

            if (round > 0) {
                game = game.startNextRound(new Random(seed++), Instant.now());
            }

            game = passAllSoledad(game).selectTrump(Suit.COPAS);

            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var loser = players.stream()
                .filter(p -> !p.equals(playerWhoGoes) && !p.equals(teammate))
                .findFirst().orElseThrow();

            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            // The opposing side takes five; the going pair pays.
            for (var i = 0; i < Round.BASAS_TO_WIN; i++) {
                game = playAndCompleteBasa(game, loser);
            }
        }

        if (!game.isEnded()) {
            throw new IllegalStateException("Failed to create ended game - unexpected coin state");
        }
        return game;
    }
}
