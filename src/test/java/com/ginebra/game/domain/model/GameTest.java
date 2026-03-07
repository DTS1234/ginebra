package com.ginebra.game.domain.model;

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

        for (var i = 0; i < basasToWin; i++) {
            game = playAndCompleteBasa(game, basaWinner);
        }
        return game;
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
        void shouldInitializeFivePlayersWithTwentyCoinsEach() {
            final var players = createPlayers();
            final var game = createGame(players);

            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(20);
            }
            assertThat(game.coinBalances()).hasSize(5);
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
        void shouldEndRoundWhenTeamWinsFiveBasas() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.currentRound().get().result()).isPresent();
            assertThat(game.currentRound().get().result().get()).isInstanceOf(RoundResult.Win.class);
        }

        @Test
        void shouldCalculateCoinsOnRoundEndWin() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            // Winners (team of two) should have 20 + 2 = 22 (or more with bonuses)
            assertThat(game.getCoins(playerWhoGoes)).isGreaterThanOrEqualTo(22);
            assertThat(game.getCoins(teammate)).isGreaterThanOrEqualTo(22);

            // Losers should have 20 - 2 = 18
            final var losers = players.stream()
                .filter(p -> !p.equals(playerWhoGoes) && !p.equals(teammate))
                .toList();
            for (final var loser : losers) {
                assertThat(game.getCoins(loser)).isEqualTo(18);
            }
        }

        @Test
        void shouldCalculateCoinsOnRoundEndDraw() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);

            // Play 8 basas without teams — results in Draw
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }

            // All players should get +1 coin = 21
            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(21);
            }
        }

        @Test
        void shouldNotEndGameWhenAllPlayersHavePositiveCoins() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            // After one round losing, losers have 18 — still positive
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

    @Nested
    class CoinCalculation {

        @Test
        void shouldGivePlusTwoToWinningTeam() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            // Base reward is +2, may have bonuses
            assertThat(game.getCoins(playerWhoGoes)).isGreaterThanOrEqualTo(22);
            assertThat(game.getCoins(teammate)).isGreaterThanOrEqualTo(22);
        }

        @Test
        void shouldGiveMinusTwoToLosingTeam() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);
            final var playerWhoGoes = game.currentRound().get().playerWhoGoes();
            final var teammate = players.stream()
                .filter(p -> !p.equals(playerWhoGoes))
                .findFirst().orElseThrow();
            final var teams = Teams.of(playerWhoGoes, teammate, new HashSet<>(players));
            game = game.setTeams(teams);

            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            final var losers = players.stream()
                .filter(p -> !p.equals(playerWhoGoes) && !p.equals(teammate))
                .toList();
            for (final var loser : losers) {
                assertThat(game.getCoins(loser)).isEqualTo(18);
            }
        }

        @Test
        void shouldGivePlusOneToAllOnDraw() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);

            // No teams set — 8 basas leads to Draw
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }

            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(21);
            }
        }

        @Test
        void shouldNotGiveBonusOnDraw() {
            final var players = createPlayers();
            var game = createGameWithTrump(players, Suit.COPAS);

            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }

            // Draw always gives exactly +1, no bonuses
            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(21);
            }
        }
    }

    @Nested
    class BonusCalculation {

        @Test
        void shouldDetectDuendeCorrectly() {
            // Duende = Espadilla + Basto in hand
            final var hand = List.of(
                Card.espadilla(),
                Card.basto(),
                new Card(Suit.COPAS, Rank.REY),
                new Card(Suit.COPAS, Rank.CABALLO),
                new Card(Suit.OROS, Rank.TRES),
                new Card(Suit.OROS, Rank.CUATRO),
                new Card(Suit.OROS, Rank.CINCO),
                new Card(Suit.OROS, Rank.SEIS)
            );

            assertThat(hand.stream().anyMatch(Card::isEspadilla)).isTrue();
            assertThat(hand.stream().anyMatch(Card::isBasto)).isTrue();
        }

        @Test
        void shouldDetectEstucheCorrectly() {
            // Estuche = Espadilla + Basto + Manilla
            // When trump is COPAS, Manilla is 7 of COPAS
            final var hand = List.of(
                Card.espadilla(),
                Card.basto(),
                new Card(Suit.COPAS, Rank.SIETE), // Manilla when COPAS is trump
                new Card(Suit.COPAS, Rank.REY),
                new Card(Suit.OROS, Rank.TRES),
                new Card(Suit.OROS, Rank.CUATRO),
                new Card(Suit.OROS, Rank.CINCO),
                new Card(Suit.OROS, Rank.SEIS)
            );

            assertThat(hand.stream().anyMatch(Card::isEspadilla)).isTrue();
            assertThat(hand.stream().anyMatch(Card::isBasto)).isTrue();
            assertThat(hand.stream().anyMatch(c -> c.isManilla(Suit.COPAS))).isTrue();
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

            assertThat(game.getCoins(players.get(0))).isEqualTo(20);
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

            // Round 1: play to completion with draw (no teams)
            game = passAllSoledad(game).selectTrump(Suit.COPAS);
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }
            assertThat(game.isRoundComplete()).isTrue();

            final var round1Starter = game.currentRound().get().playerWhoGoes();
            final var round1StarterIndex = players.indexOf(round1Starter);
            final var expectedRound2Starter = players.get((round1StarterIndex + 1) % 5);

            // Start round 2
            game = game.startNextRound(new Random(99L), Instant.now());
            assertThat(game.currentRound().get().roundNumber()).isEqualTo(2);
            assertThat(game.currentRound().get().playerWhoGoes()).isEqualTo(expectedRound2Starter);
            assertThat(game.completedRounds()).hasSize(1);

            // Round 2: play to completion with draw
            game = passAllSoledad(game).selectTrump(Suit.OROS);
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }
            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.roundNumber()).isEqualTo(2);
        }

        @Test
        void shouldTrackCoinBalancesAcrossMultipleRounds() {
            final var players = createPlayers();
            var game = createGame(players);

            // Round 1: draw (+1 each)
            game = passAllSoledad(game).selectTrump(Suit.COPAS);
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }

            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(21);
            }

            // Round 2: draw (+1 each again)
            game = game.startNextRound(new Random(99L), Instant.now());
            game = passAllSoledad(game).selectTrump(Suit.OROS);
            for (var i = 0; i < 8; i++) {
                game = playAndCompleteBasa(game, players.get(i % 5));
            }

            for (final var player : players) {
                assertThat(game.getCoins(player)).isEqualTo(22);
            }
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

            // Play 5 basas — team of two wins
            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, playerWhoGoes);
            }

            // Verify round is complete
            assertThat(game.isRoundComplete()).isTrue();
            assertThat(game.currentRound().get().result().get())
                .isInstanceOf(RoundResult.Win.class);

            // Verify coins updated
            assertThat(game.getCoins(playerWhoGoes)).isGreaterThanOrEqualTo(22);
            assertThat(game.getCoins(teammate)).isGreaterThanOrEqualTo(22);

            // Game should still be in progress (no bankruptcy)
            assertThat(game.isInProgress()).isTrue();
        }
    }

    // === Helpers for creating specific game states ===

    /**
     * Creates a game where one round is complete (draw, no teams).
     */
    private Game completeOneRound(List<PlayerId> players) {
        var game = createGameWithTrump(players, Suit.COPAS);
        for (var i = 0; i < 8; i++) {
            game = playAndCompleteBasa(game, players.get(i % 5));
        }
        return game;
    }

    /**
     * Creates an ended game by playing many rounds until someone goes bankrupt.
     * Uses a team-of-three winning repeatedly to drain team-of-two's coins.
     */
    private Game createEndedGame(List<PlayerId> players) {
        var game = createGame(players);
        var seed = 100L;

        // Each round, team of three wins → losers (team of two) lose 2 coins each
        // After 10 rounds of losing, a player at 20 coins goes to 0 → bankrupt
        for (var round = 0; round < 10; round++) {
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

            // Team of three wins (loser is on team of three)
            for (var i = 0; i < 5; i++) {
                game = playAndCompleteBasa(game, loser);
            }
        }

        // If we get here without ENDED, force it by creating another scenario
        // But with 10 rounds of losing 2 coins, someone at 20 should be at 0
        if (!game.isEnded()) {
            throw new IllegalStateException("Failed to create ended game — unexpected coin state");
        }
        return game;
    }
}
