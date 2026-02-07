package com.ginebra.game.domain.model;

import com.ginebra.identity.domain.PlayerId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundTest {

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

    private Map<PlayerId, List<Card>> dealHands(List<PlayerId> players) {
        final var deck = Deck.create().shuffle(new Random(42));
        final var dealt = deck.deal(5);
        final var hands = new HashMap<PlayerId, List<Card>>();
        for (var i = 0; i < players.size(); i++) {
            hands.put(players.get(i), dealt.get(i));
        }
        return hands;
    }

    private Round createRound(List<PlayerId> players) {
        return Round.start(1, players.get(0), players, dealHands(players));
    }

    private Round createRoundInProgress(List<PlayerId> players) {
        return createRound(players).withTrump(Suit.COPAS);
    }

    private PlayedCard playedCard(PlayerId player, Card card) {
        return PlayedCard.of(player, card, Instant.now());
    }

    /**
     * Plays 5 cards into the current basa (one per player in clockwise order)
     * using the first card from each player's hand.
     */
    private Round playFullBasa(Round round, List<PlayerId> players) {
        for (var i = 0; i < 5; i++) {
            final var currentPlayer = round.currentPlayer().orElseThrow();
            final var card = round.getHand(currentPlayer).get(0);
            round = round.withCardPlayed(currentPlayer, card, Instant.now());
        }
        return round;
    }

    /**
     * Plays a full basa and completes it with the given winner.
     */
    private Round playAndCompleteBasa(Round round, List<PlayerId> players, PlayerId winner) {
        round = playFullBasa(round, players);
        return round.completeBasa(winner);
    }

    @Nested
    class Start {

        @Test
        void shouldCreateRoundInWaitingForTrumpStatus() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            final var round = Round.start(1, players.get(0), players, hands);

            assertThat(round.status()).isEqualTo(RoundStatus.WAITING_FOR_TRUMP);
            assertThat(round.isWaitingForTrump()).isTrue();
        }

        @Test
        void shouldSetCorrectRoundNumber() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            final var round = Round.start(3, players.get(0), players, hands);

            assertThat(round.roundNumber()).isEqualTo(3);
        }

        @Test
        void shouldSetCorrectPlayerWhoGoes() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            final var round = Round.start(1, players.get(2), players, hands);

            assertThat(round.playerWhoGoes()).isEqualTo(players.get(2));
        }

        @Test
        void shouldSetCorrectPlayerOrder() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            final var round = Round.start(1, players.get(0), players, hands);

            assertThat(round.playerOrder()).containsExactlyElementsOf(players);
        }

        @Test
        void shouldInitializeWithEmptyCompletedBasas() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.completedBasas()).isEmpty();
        }

        @Test
        void shouldInitializeWithNoCurrentBasa() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.currentBasa()).isEmpty();
        }

        @Test
        void shouldInitializeWithNoTeams() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.teams()).isEmpty();
        }

        @Test
        void shouldInitializeWithNoResult() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.result()).isEmpty();
        }

        @Test
        void shouldDealEightCardsPerPlayer() {
            final var players = createPlayers();
            final var round = createRound(players);

            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(8);
            }
        }

        @Test
        void shouldHaveFortyTotalUniqueCards() {
            final var players = createPlayers();
            final var round = createRound(players);

            final var allCards = players.stream()
                .flatMap(p -> round.getHand(p).stream())
                .collect(Collectors.toSet());

            assertThat(allCards).hasSize(40);
        }

        @Test
        void shouldRejectRoundNumberLessThanOne() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            assertThatThrownBy(() -> Round.start(0, players.get(0), players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roundNumber must be >= 1");
        }

        @Test
        void shouldRejectNegativeRoundNumber() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            assertThatThrownBy(() -> Round.start(-1, players.get(0), players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roundNumber must be >= 1");
        }

        @Test
        void shouldRejectWrongPlayerCount() {
            final var players = List.of(PlayerId.generate(), PlayerId.generate(), PlayerId.generate());
            final var fivePlayers = createPlayers();
            final var hands = dealHands(fivePlayers);

            assertThatThrownBy(() -> Round.start(1, players.get(0), players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerOrder must have 5 players");
        }

        @Test
        void shouldRejectPlayerWhoGoesNotInPlayerOrder() {
            final var players = createPlayers();
            final var outsider = PlayerId.generate();
            final var hands = dealHands(players);

            assertThatThrownBy(() -> Round.start(1, outsider, players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerWhoGoes must be in playerOrder");
        }

        @Test
        void shouldRejectWrongHandSize() {
            final var players = createPlayers();
            final var hands = new HashMap<PlayerId, List<Card>>();
            final var deck = Deck.create().shuffle(new Random(42));
            final var dealt = deck.deal(5);

            // Give first player only 7 cards
            hands.put(players.get(0), dealt.get(0).subList(0, 7));
            for (var i = 1; i < players.size(); i++) {
                hands.put(players.get(i), dealt.get(i));
            }

            assertThatThrownBy(() -> Round.start(1, players.get(0), players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each hand must have 8 cards");
        }

        @Test
        void shouldRejectDuplicateCardsAcrossHands() {
            final var players = createPlayers();
            final var deck = Deck.create().shuffle(new Random(42));
            final var dealt = deck.deal(5);
            final var hands = new HashMap<PlayerId, List<Card>>();

            // Give all players the same hand as player 0
            for (final var player : players) {
                hands.put(player, dealt.get(0));
            }

            assertThatThrownBy(() -> Round.start(1, players.get(0), players, hands))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate cards found");
        }

        @Test
        void shouldRejectNullPlayerWhoGoes() {
            final var players = createPlayers();
            final var hands = dealHands(players);

            assertThatThrownBy(() -> Round.start(1, null, players, hands))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerWhoGoes must not be null");
        }

        @Test
        void shouldRejectNullPlayerOrder() {
            final var hands = dealHands(createPlayers());
            final var somePlayer = hands.keySet().iterator().next();

            assertThatThrownBy(() -> Round.start(1, somePlayer, null, hands))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerOrder must not be null");
        }

        @Test
        void shouldRejectNullHands() {
            final var players = createPlayers();

            assertThatThrownBy(() -> Round.start(1, players.get(0), players, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hands must not be null");
        }
    }

    @Nested
    class QueryMethods {

        @Test
        void trumpSuitShouldReturnEmptyBeforeSelection() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.trumpSuit()).isEmpty();
        }

        @Test
        void currentPlayerShouldReturnEmptyWhenWaitingForTrump() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.currentPlayer()).isEmpty();
        }

        @Test
        void currentPlayerShouldReturnStarterWhenBasaJustStarted() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);

            assertThat(round.currentPlayer()).contains(players.get(0));
        }

        @Test
        void currentPlayerShouldAdvanceClockwiseAsCardsArePlayed() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // First player plays
            assertThat(round.currentPlayer()).contains(players.get(0));
            final var card0 = round.getHand(players.get(0)).get(0);
            round = round.withCardPlayed(players.get(0), card0, Instant.now());

            // Second player's turn
            assertThat(round.currentPlayer()).contains(players.get(1));
            final var card1 = round.getHand(players.get(1)).get(0);
            round = round.withCardPlayed(players.get(1), card1, Instant.now());

            // Third player's turn
            assertThat(round.currentPlayer()).contains(players.get(2));
        }

        @Test
        void currentPlayerShouldWrapAroundClockwise() {
            final var players = createPlayers();
            // Create a round where player at index 3 starts
            final var hands = dealHands(players);
            var round = Round.start(1, players.get(3), players, hands).withTrump(Suit.COPAS);

            // Player 3 starts, then 4, then wraps to 0
            assertThat(round.currentPlayer()).contains(players.get(3));

            final var card3 = round.getHand(players.get(3)).get(0);
            round = round.withCardPlayed(players.get(3), card3, Instant.now());
            assertThat(round.currentPlayer()).contains(players.get(4));

            final var card4 = round.getHand(players.get(4)).get(0);
            round = round.withCardPlayed(players.get(4), card4, Instant.now());
            assertThat(round.currentPlayer()).contains(players.get(0));
        }

        @Test
        void getNextBasaStarterShouldReturnPlayerWhoGoesInitially() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.getNextBasaStarter()).isEqualTo(players.get(0));
        }

        @Test
        void getNextBasaStarterShouldReturnPlayerToRightAfterFirstBasa() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            round = playAndCompleteBasa(round, players, players.get(0));

            // Next basa starter should be player to the right of player[0], which is player[1]
            assertThat(round.getNextBasaStarter()).isEqualTo(players.get(1));
        }

        @Test
        void basasWonByShouldReturnZeroForPlayerWithNoWins() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThat(round.basasWonBy(players.get(0))).isZero();
        }

        @Test
        void basasWonByShouldCountCorrectly() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Complete two basas, both won by player[0]
            round = playAndCompleteBasa(round, players, players.get(0));
            round = playAndCompleteBasa(round, players, players.get(0));

            assertThat(round.basasWonBy(players.get(0))).isEqualTo(2);
            assertThat(round.basasWonBy(players.get(1))).isZero();
        }

        @Test
        void basasWonByAllShouldReturnMapForAllPlayers() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            round = playAndCompleteBasa(round, players, players.get(0));
            round = playAndCompleteBasa(round, players, players.get(2));

            final var wonByAll = round.basasWonByAll();

            assertThat(wonByAll).hasSize(5);
            assertThat(wonByAll.get(players.get(0))).isEqualTo(1);
            assertThat(wonByAll.get(players.get(1))).isZero();
            assertThat(wonByAll.get(players.get(2))).isEqualTo(1);
            assertThat(wonByAll.get(players.get(3))).isZero();
            assertThat(wonByAll.get(players.get(4))).isZero();
        }

        @Test
        void getHandShouldReturnCorrectHand() {
            final var players = createPlayers();
            final var hands = dealHands(players);
            final var round = Round.start(1, players.get(0), players, hands);

            for (final var player : players) {
                assertThat(round.getHand(player)).containsExactlyElementsOf(hands.get(player));
            }
        }

        @Test
        void getHandShouldThrowForUnknownPlayer() {
            final var players = createPlayers();
            final var round = createRound(players);
            final var unknown = PlayerId.generate();

            assertThatThrownBy(() -> round.getHand(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player not in round");
        }

        @Test
        void statusConvenienceMethodsShouldWorkCorrectly() {
            final var players = createPlayers();

            final var waitingRound = createRound(players);
            assertThat(waitingRound.isWaitingForTrump()).isTrue();
            assertThat(waitingRound.isInProgress()).isFalse();
            assertThat(waitingRound.isComplete()).isFalse();

            final var inProgressRound = createRoundInProgress(players);
            assertThat(inProgressRound.isWaitingForTrump()).isFalse();
            assertThat(inProgressRound.isInProgress()).isTrue();
            assertThat(inProgressRound.isComplete()).isFalse();
        }

        @Test
        void getBasaCountShouldIncludeCurrentBasa() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Current basa exists, no completed basas
            assertThat(round.getBasaCount()).isEqualTo(1);

            round = playAndCompleteBasa(round, players, players.get(0));

            // One completed basa plus new current basa
            assertThat(round.getBasaCount()).isEqualTo(2);
        }
    }

    @Nested
    class WithTrump {

        @Test
        void shouldSetTrumpSuit() {
            final var players = createPlayers();
            final var round = createRound(players);

            final var withTrump = round.withTrump(Suit.OROS);

            assertThat(withTrump.trumpSuit()).contains(Suit.OROS);
        }

        @Test
        void shouldTransitionToInProgress() {
            final var players = createPlayers();
            final var round = createRound(players);

            final var withTrump = round.withTrump(Suit.COPAS);

            assertThat(withTrump.status()).isEqualTo(RoundStatus.IN_PROGRESS);
            assertThat(withTrump.isInProgress()).isTrue();
        }

        @Test
        void shouldCreateFirstBasa() {
            final var players = createPlayers();
            final var round = createRound(players);

            final var withTrump = round.withTrump(Suit.ESPADAS);

            assertThat(withTrump.currentBasa()).isPresent();
            final var basa = withTrump.currentBasa().get();
            assertThat(basa.basaNumber()).isEqualTo(1);
            assertThat(basa.startingPlayer()).isEqualTo(players.get(0));
        }

        @Test
        void shouldRejectNullTrump() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThatThrownBy(() -> round.withTrump(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trump must not be null");
        }

        @Test
        void shouldRejectTrumpWhenAlreadySelected() {
            final var players = createPlayers();
            final var round = createRound(players).withTrump(Suit.COPAS);

            assertThatThrownBy(() -> round.withTrump(Suit.OROS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trump already selected");
        }
    }

    @Nested
    class WithCardPlayed {

        @Test
        void shouldAddCardToCurrentBasa() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            final var card = round.getHand(players.get(0)).get(0);
            round = round.withCardPlayed(players.get(0), card, Instant.now());

            assertThat(round.currentBasa().get().cardCount()).isEqualTo(1);
        }

        @Test
        void shouldRemoveCardFromPlayersHand() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            final var handBefore = round.getHand(players.get(0));
            final var card = handBefore.get(0);
            round = round.withCardPlayed(players.get(0), card, Instant.now());

            assertThat(round.getHand(players.get(0))).hasSize(7);
            assertThat(round.getHand(players.get(0))).doesNotContain(card);
        }

        @Test
        void shouldValidateExpectedPlayerTurn() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            final var card = round.getHand(players.get(1)).get(0);

            // Player 1 trying to play when it's player 0's turn
            assertThatThrownBy(() -> round.withCardPlayed(players.get(1), card, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not player's turn");
        }

        @Test
        void shouldRejectCardNotInHand() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            // Use a card from player 1's hand (not in player 0's hand)
            final var otherCard = round.getHand(players.get(1)).get(0);

            assertThatThrownBy(() -> round.withCardPlayed(players.get(0), otherCard, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Player does not have card");
        }

        @Test
        void shouldRejectPlayWhenNotInProgress() {
            final var players = createPlayers();
            final var round = createRound(players); // WAITING_FOR_TRUMP
            final var card = round.getHand(players.get(0)).get(0);

            assertThatThrownBy(() -> round.withCardPlayed(players.get(0), card, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot play card");
        }

        @Test
        void shouldRejectNullPlayerId() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            final var card = round.getHand(players.get(0)).get(0);

            assertThatThrownBy(() -> round.withCardPlayed(null, card, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerId must not be null");
        }

        @Test
        void shouldRejectNullCard() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);

            assertThatThrownBy(() -> round.withCardPlayed(players.get(0), null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("card must not be null");
        }

        @Test
        void shouldRejectNullPlayedAt() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            final var card = round.getHand(players.get(0)).get(0);

            assertThatThrownBy(() -> round.withCardPlayed(players.get(0), card, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playedAt must not be null");
        }
    }

    @Nested
    class CompleteBasa {

        @Test
        void shouldCompletedCurrentBasa() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            round = playFullBasa(round, players);

            final var completed = round.completeBasa(players.get(0));

            assertThat(completed.completedBasas()).hasSize(1);
            assertThat(completed.completedBasas().get(0).winner()).contains(players.get(0));
        }

        @Test
        void shouldStartNextBasaWithCorrectStarter() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            round = playFullBasa(round, players);

            final var afterComplete = round.completeBasa(players.get(0));

            // Next basa should start with player to the right of the first basa's starter (players[0])
            // That's players[1]
            assertThat(afterComplete.currentBasa()).isPresent();
            assertThat(afterComplete.currentBasa().get().startingPlayer()).isEqualTo(players.get(1));
            assertThat(afterComplete.currentBasa().get().basaNumber()).isEqualTo(2);
        }

        @Test
        void shouldRejectWhenNoCurrentBasa() {
            final var players = createPlayers();
            final var round = createRound(players); // No current basa

            assertThatThrownBy(() -> round.completeBasa(players.get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No current basa to complete");
        }

        @Test
        void shouldRejectWhenBasaNotFull() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Only play 3 cards
            for (var i = 0; i < 3; i++) {
                final var currentPlayer = round.currentPlayer().orElseThrow();
                final var card = round.getHand(currentPlayer).get(0);
                round = round.withCardPlayed(currentPlayer, card, Instant.now());
            }
            final var incompleteRound = round;

            assertThatThrownBy(() -> incompleteRound.completeBasa(players.get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Current basa not ready to complete");
        }

        @Test
        void shouldRejectNullWinnerId() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            round = playFullBasa(round, players);
            final var fullRound = round;

            assertThatThrownBy(() -> fullRound.completeBasa(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("winnerId must not be null");
        }
    }

    @Nested
    class WithTeams {

        @Test
        void shouldSetTeams() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );

            final var withTeams = round.withTeams(teams);

            assertThat(withTeams.teams()).isPresent();
            assertThat(withTeams.teams().get().teamOfTwo())
                .containsExactlyInAnyOrder(players.get(0), players.get(1));
        }

        @Test
        void shouldRejectNullTeams() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);

            assertThatThrownBy(() -> round.withTeams(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("teams must not be null");
        }

        @Test
        void shouldRejectWhenTeamsAlreadySet() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            final var roundWithTeams = round.withTeams(teams);

            assertThatThrownBy(() -> roundWithTeams.withTeams(teams))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Teams already set");
        }

        @Test
        void shouldRejectPlayerWhoGoesNotOnTeamOfTwo() {
            final var players = createPlayers();
            final var round = createRoundInProgress(players);
            // Create teams where playerWhoGoes (players[0]) is NOT on team of two
            final var teams = new Teams(
                Set.of(players.get(1), players.get(2)),
                Set.of(players.get(0), players.get(3), players.get(4))
            );

            assertThatThrownBy(() -> round.withTeams(teams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerWhoGoes must be on team of two");
        }
    }

    @Nested
    class RoundEndLogic {

        @Test
        void shouldEndWhenTeamOfTwoReachesFiveBasas() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // Team of two wins 5 basas (alternating between player 0 and 1)
            for (var i = 0; i < 5; i++) {
                round = playFullBasa(round, players);
                final var winner = (i % 2 == 0) ? players.get(0) : players.get(1);
                round = round.completeBasa(winner);
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            assertThat(round.result().get()).isInstanceOf(RoundResult.Win.class);
            final var win = (RoundResult.Win) round.result().get();
            assertThat(win.winners()).containsExactlyInAnyOrder(players.get(0), players.get(1));
        }

        @Test
        void shouldEndWhenTeamOfThreeReachesFiveBasas() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // Team of three wins 5 basas (player 2, 3, 4 rotate wins)
            for (var i = 0; i < 5; i++) {
                round = playFullBasa(round, players);
                final var winner = players.get(2 + (i % 3));
                round = round.completeBasa(winner);
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            assertThat(round.result().get()).isInstanceOf(RoundResult.Win.class);
            final var win = (RoundResult.Win) round.result().get();
            assertThat(win.winners()).containsExactlyInAnyOrder(players.get(2), players.get(3), players.get(4));
        }

        @Test
        void shouldDrawWhenAllEightBasasPlayedWithFourFourSplit() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // 4 basas for team of two
            for (var i = 0; i < 4; i++) {
                round = playFullBasa(round, players);
                round = round.completeBasa(players.get(0));
            }
            // 4 basas for team of three
            for (var i = 0; i < 4; i++) {
                round = playFullBasa(round, players);
                round = round.completeBasa(players.get(2));
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            assertThat(round.result().get()).isInstanceOf(RoundResult.Draw.class);
        }

        @Test
        void shouldContinueWhileNeitherTeamHasFiveBasas() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // Play 4 basas, team of two wins 3, team of three wins 1
            for (var i = 0; i < 3; i++) {
                round = playFullBasa(round, players);
                round = round.completeBasa(players.get(0));
            }
            round = playFullBasa(round, players);
            round = round.completeBasa(players.get(2));

            assertThat(round.isInProgress()).isTrue();
            assertThat(round.result()).isEmpty();
        }

        @Test
        void shouldDrawAtMaxBasasWithoutTeams() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Without teams set, play all 8 basas
            for (var i = 0; i < 8; i++) {
                round = playFullBasa(round, players);
                round = round.completeBasa(players.get(i % 5));
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            assertThat(round.result().get()).isInstanceOf(RoundResult.Draw.class);
        }

        @Test
        void shouldNotEndByTeamScoreWithoutTeamsSet() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Without teams, 5 basas won by same player doesn't trigger a win
            for (var i = 0; i < 5; i++) {
                round = playFullBasa(round, players);
                round = round.completeBasa(players.get(0));
            }

            // Should still be in progress (no teams to check against)
            assertThat(round.isInProgress()).isTrue();
        }
    }

    @Nested
    class Immutability {

        @Test
        void withTrumpShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = createRound(players);

            final var withTrump = original.withTrump(Suit.COPAS);

            assertThat(withTrump).isNotSameAs(original);
            assertThat(original.isWaitingForTrump()).isTrue();
            assertThat(original.trumpSuit()).isEmpty();
        }

        @Test
        void withCardPlayedShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = createRoundInProgress(players);
            final var originalHand = original.getHand(players.get(0));
            final var card = originalHand.get(0);

            final var withCard = original.withCardPlayed(players.get(0), card, Instant.now());

            assertThat(withCard).isNotSameAs(original);
            // Original hand should still have 8 cards
            assertThat(original.getHand(players.get(0))).hasSize(8);
            assertThat(original.getHand(players.get(0))).contains(card);
        }

        @Test
        void completeBasaShouldReturnNewInstance() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            round = playFullBasa(round, players);

            final var before = round;
            final var after = round.completeBasa(players.get(0));

            assertThat(after).isNotSameAs(before);
            assertThat(before.completedBasas()).isEmpty();
        }

        @Test
        void withTeamsShouldReturnNewInstance() {
            final var players = createPlayers();
            final var original = createRoundInProgress(players);
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );

            final var withTeams = original.withTeams(teams);

            assertThat(withTeams).isNotSameAs(original);
            assertThat(original.teams()).isEmpty();
        }

        @Test
        void playerOrderShouldBeUnmodifiable() {
            final var players = createPlayers();
            final var round = createRound(players);

            assertThatThrownBy(() -> round.playerOrder().add(PlayerId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void completedBasasShouldBeUnmodifiable() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);
            round = playAndCompleteBasa(round, players, players.get(0));

            final var completedBasas = round.completedBasas();
            assertThatThrownBy(() -> completedBasas.add(Basa.start(1, PlayerId.generate())))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void handsShouldBeUnmodifiable() {
            final var players = createPlayers();
            final var round = createRound(players);

            final var hand = round.getHand(players.get(0));
            assertThatThrownBy(() -> hand.add(new Card(Suit.COPAS, Rank.REY)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class RealGameScenarios {

        @Test
        void shouldHandleFullRoundFlowWithTeamOfTwoWinning() {
            final var players = createPlayers();
            var round = createRound(players);

            // Step 1: Select trump
            assertThat(round.isWaitingForTrump()).isTrue();
            round = round.withTrump(Suit.COPAS);
            assertThat(round.isInProgress()).isTrue();
            assertThat(round.trumpSuit()).contains(Suit.COPAS);

            // Step 2: Set teams (player 0 + player 1 vs player 2,3,4)
            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // Step 3: Play basas - team of two wins 5
            for (var basaNum = 0; basaNum < 5; basaNum++) {
                // Play a full basa
                for (var cardNum = 0; cardNum < 5; cardNum++) {
                    final var currentPlayer = round.currentPlayer().orElseThrow();
                    final var card = round.getHand(currentPlayer).get(0);
                    round = round.withCardPlayed(currentPlayer, card, Instant.now());
                }

                // Complete with team of two winning
                final var winner = (basaNum % 2 == 0) ? players.get(0) : players.get(1);
                round = round.completeBasa(winner);
            }

            // Verify round is complete with team of two as winners
            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            final var result = (RoundResult.Win) round.result().get();
            assertThat(result.winners()).containsExactlyInAnyOrder(players.get(0), players.get(1));
            assertThat(round.completedBasas()).hasSize(5);
            assertThat(round.currentBasa()).isEmpty();
        }

        @Test
        void shouldHandleDrawAfterEightBasas() {
            final var players = createPlayers();
            var round = createRound(players);

            round = round.withTrump(Suit.OROS);

            final var teams = Teams.of(
                players.get(0),
                players.get(1),
                new HashSet<>(players)
            );
            round = round.withTeams(teams);

            // Play 8 basas: 4 wins for team of two, 4 for team of three
            for (var basaNum = 0; basaNum < 8; basaNum++) {
                for (var cardNum = 0; cardNum < 5; cardNum++) {
                    final var currentPlayer = round.currentPlayer().orElseThrow();
                    final var card = round.getHand(currentPlayer).get(0);
                    round = round.withCardPlayed(currentPlayer, card, Instant.now());
                }

                // Alternate winners between teams
                final var winner = (basaNum < 4) ? players.get(0) : players.get(2);
                round = round.completeBasa(winner);
            }

            assertThat(round.isComplete()).isTrue();
            assertThat(round.result()).isPresent();
            assertThat(round.result().get()).isInstanceOf(RoundResult.Draw.class);
            assertThat(round.completedBasas()).hasSize(8);
        }

        @Test
        void shouldTrackHandSizesThroughMultipleBasas() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Each player starts with 8 cards
            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(8);
            }

            // After first basa, each player has 7 cards
            round = playAndCompleteBasa(round, players, players.get(0));
            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(7);
            }

            // After second basa, each player has 6 cards
            round = playAndCompleteBasa(round, players, players.get(1));
            for (final var player : players) {
                assertThat(round.getHand(player)).hasSize(6);
            }
        }

        @Test
        void shouldAdvanceBasaStartersThroughRound() {
            final var players = createPlayers();
            var round = createRoundInProgress(players);

            // Basa 1 starts with players[0]
            assertThat(round.currentBasa().get().startingPlayer()).isEqualTo(players.get(0));
            round = playAndCompleteBasa(round, players, players.get(0));

            // Basa 2 starts with players[1] (right of players[0])
            assertThat(round.currentBasa().get().startingPlayer()).isEqualTo(players.get(1));
            round = playAndCompleteBasa(round, players, players.get(0));

            // Basa 3 starts with players[2] (right of players[1])
            assertThat(round.currentBasa().get().startingPlayer()).isEqualTo(players.get(2));
            round = playAndCompleteBasa(round, players, players.get(0));

            // Basa 4 starts with players[3] (right of players[2])
            assertThat(round.currentBasa().get().startingPlayer()).isEqualTo(players.get(3));
        }
    }
}
