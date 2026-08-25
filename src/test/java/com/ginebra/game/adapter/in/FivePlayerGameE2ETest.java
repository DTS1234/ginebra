package com.ginebra.game.adapter.in;

import com.ginebra.connection.application.ConnectionTracker;
import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Suit;
import com.ginebra.identity.adapter.out.InMemorySessionStore;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import com.ginebra.lobby.domain.GameId;
import com.ginebra.support.LegalMoves;
import com.ginebra.support.LobbyFixture;
import com.ginebra.support.StompTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the Phase 4 exit criterion:
 * five clients connect over a real WebSocket, play cards and see each other's moves in real time.
 *
 * Everything here goes through the public surface - REST for auth and the lobby,
 * STOMP frames for the game - so nothing is asserted against internal state that a
 * real client could not observe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Five clients playing one game over WebSocket")
class FivePlayerGameE2ETest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Suit TRUMP = Suit.COPAS;
    private static final int PLAYERS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemorySessionStore sessionStore;

    @Autowired
    private InMemoryGameRepository gameRepository;

    @Autowired
    private ConnectionTracker connectionTracker;

    private LobbyFixture lobby;
    private final List<StompTestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        roomRepository.clear();
        sessionStore.clear();
        gameRepository.clear();
        connectionTracker.clear();
        lobby = new LobbyFixture(restTemplate);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(StompTestClient::close);
        clients.clear();
    }

    @Test
    void shouldDealEightPrivateCardsToEachOfFiveConnectedClients() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();

        // Act
        final var connected = connectAll(table);

        // Assert: every client gets its own hand, and the five hands are the whole deck
        final var allCards = new ArrayList<Card>();
        for (final var player : table.players()) {
            final var hand = connected.get(player.id()).hand();
            assertThat(hand).as("hand of %s", player.displayName()).hasSize(8);
            allCards.addAll(hand);
        }
        assertThat(allCards).hasSize(40);
        assertThat(new HashSet<>(allCards)).as("no card is dealt twice").hasSize(40);
    }

    @Test
    void shouldExposeOnlyTheReceivingPlayersHandInGameState() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);
        final var first = connected.get(table.players().get(0).id());
        final var second = connected.get(table.players().get(1).id());

        // Act
        final var rawState = first.messagesOfType("GAME_STATE").get(0).toString();

        // Assert: the two players hold different cards, and the state sent to the first
        // carries exactly the eight cards of its own hand - nobody else's
        assertThat(first.hand()).doesNotContainAnyElementsOf(second.hand());
        assertThat(countOccurrences(rawState, "rank=")).as("cards visible in GAME_STATE").isEqualTo(8);
        assertThat(first.round().get("soledadPlayer")).isNull();
    }

    @Test
    void shouldRegisterAllFivePlayersAsConnected() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();

        // Act
        connectAll(table);

        // Assert
        final var gameId = new GameId(UUID.fromString(table.gameId()));
        assertThat(connectionTracker.getConnectedPlayers(gameId)).hasSize(PLAYERS);
        for (final var player : table.players()) {
            assertThat(connectionTracker.isConnected(new PlayerId(UUID.fromString(player.id())))).isTrue();
        }
    }

    @Test
    void shouldBroadcastSoledadWindowProgressToAllFiveClients() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);

        // Act
        passSoledadForEveryone(table, connected);

        // Assert: all five clients saw all five passes and the window closing
        for (final var client : connected.values()) {
            final var passes = client.awaitMessages("SOLEDAD_PASSED", PLAYERS, TIMEOUT);
            assertThat(passes).hasSize(PLAYERS);

            final var closed = StompTestClient.payloadOf(client.awaitMessage("SOLEDAD_WINDOW_CLOSED", TIMEOUT));
            assertThat(closed.get("declared")).isEqualTo(false);
            assertThat(closed.get("awaitingTrumpFrom")).isEqualTo(playerWhoGoes(connected));
        }
    }

    @Test
    void shouldBroadcastSoledadDeclarationToAllFiveClients() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);
        final var starter = playerWhoGoes(connected);
        final var declarer = table.players().stream()
            .map(LobbyFixture.Player::id)
            .filter(id -> !id.equals(starter))
            .findFirst()
            .orElseThrow();

        // Act
        connected.get(declarer).declareSoledad(table.gameId());

        // Assert: everyone is told who declared and that the window is shut
        for (final var client : connected.values()) {
            final var declared = StompTestClient.payloadOf(client.awaitMessage("SOLEDAD_DECLARED", TIMEOUT));
            assertThat(declared.get("byPlayer")).isEqualTo(declarer);

            final var closed = StompTestClient.payloadOf(client.awaitMessage("SOLEDAD_WINDOW_CLOSED", TIMEOUT));
            assertThat(closed.get("declared")).isEqualTo(true);
        }
    }

    @Test
    void shouldBroadcastTrumpSelectionToAllFiveClients() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);
        passSoledadForEveryone(table, connected);

        // Act
        final var starter = playerWhoGoes(connected);
        connected.get(starter).selectTrump(table.gameId(), TRUMP);

        // Assert
        for (final var client : connected.values()) {
            final var payload = StompTestClient.payloadOf(client.awaitMessage("TRUMP_SELECTED", TIMEOUT));
            assertThat(payload.get("suit")).isEqualTo(TRUMP.name());
            assertThat(payload.get("byPlayer")).isEqualTo(starter);
            assertThat(payload.get("currentTurn")).isEqualTo(starter);
        }
    }

    @Test
    void shouldBroadcastEveryCardOfABasaToAllFiveClientsInRealTime() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);
        passSoledadForEveryone(table, connected);
        final var starter = playerWhoGoes(connected);
        connected.get(starter).selectTrump(table.gameId(), TRUMP);
        awaitEverywhere(connected, "TRUMP_SELECTED", 1);

        // Act: play one complete basa, each client playing on its own turn
        final var played = playOneBasa(table, connected, starter);

        // Assert: every client saw all five cards, in the same order
        for (final var client : connected.values()) {
            final var cardsPlayed = client.awaitMessages("CARD_PLAYED", PLAYERS, TIMEOUT);
            final var seen = cardsPlayed.stream()
                .map(StompTestClient::payloadOf)
                .map(payload -> payload.get("playerId") + ":" + cardOf(payload))
                .toList();
            assertThat(seen).as("cards seen by %s", client.name()).isEqualTo(played);
        }

        // Assert: every client saw the same basa result
        final var winners = new HashSet<Object>();
        for (final var client : connected.values()) {
            final var payload = StompTestClient.payloadOf(client.awaitMessage("BASA_WON", TIMEOUT));
            assertThat(payload.get("basaNumber")).isEqualTo(1);
            assertThat((List<?>) payload.get("cards")).hasSize(PLAYERS);
            assertThat(payload.get("nextBasaNumber")).isEqualTo(2);
            winners.add(payload.get("winner"));
        }
        assertThat(winners).as("all clients agree on the winner").hasSize(1);
        assertThat(table.byId((String) winners.iterator().next())).isNotNull();
    }

    @Test
    void shouldSendTurnErrorOnlyToTheClientThatPlayedOutOfTurn() throws Exception {
        // Arrange
        final var table = lobby.seatFivePlayers();
        final var connected = connectAll(table);
        passSoledadForEveryone(table, connected);
        final var starter = playerWhoGoes(connected);
        connected.get(starter).selectTrump(table.gameId(), TRUMP);
        awaitEverywhere(connected, "TRUMP_SELECTED", 1);

        final var offender = table.players().stream()
            .map(LobbyFixture.Player::id)
            .filter(id -> !id.equals(starter))
            .findFirst()
            .orElseThrow();

        // Act: a player who is not on turn tries to play
        final var offenderClient = connected.get(offender);
        offenderClient.playCard(table.gameId(), offenderClient.hand().get(0));

        // Assert: only that client is told, and no card reaches the table
        final var error = StompTestClient.payloadOf(offenderClient.awaitMessage("ERROR", TIMEOUT));
        assertThat(error.get("code")).isEqualTo("NOT_YOUR_TURN");

        for (final var entry : connected.entrySet()) {
            if (!entry.getKey().equals(offender)) {
                assertThat(entry.getValue().messagesOfType("ERROR"))
                    .as("%s should not see another player's error", entry.getValue().name())
                    .isEmpty();
            }
            assertThat(entry.getValue().messagesOfType("CARD_PLAYED"))
                .as("rejected play must not be broadcast")
                .isEmpty();
        }
    }

    // === Helpers ===

    private Map<String, StompTestClient> connectAll(LobbyFixture.Table table) throws Exception {
        final var connected = new LinkedHashMap<String, StompTestClient>();
        for (final var player : table.players()) {
            final var client = StompTestClient.connect(player.displayName(), port, player.token(), TIMEOUT);
            clients.add(client);
            connected.put(player.id(), client);
            client.joinGame(table.gameId());
            client.awaitMessage("GAME_STATE", TIMEOUT);
        }
        return connected;
    }

    private void passSoledadForEveryone(LobbyFixture.Table table, Map<String, StompTestClient> connected) {
        var expected = 0;
        for (final var player : table.players()) {
            connected.get(player.id()).passSoledad(table.gameId());
            expected++;
            // Pass one at a time so the window closes only after the fifth pass.
            awaitEverywhere(connected, "SOLEDAD_PASSED", expected);
        }
    }

    /**
     * Plays a full five-card basa and returns the "playerId:SUIT-RANK" sequence that was played.
     */
    private List<String> playOneBasa(
        LobbyFixture.Table table,
        Map<String, StompTestClient> connected,
        String starter
    ) {
        final var remainingHands = new HashMap<String, List<Card>>();
        connected.forEach((playerId, client) -> remainingHands.put(playerId, new ArrayList<>(client.hand())));

        final var played = new ArrayList<String>();
        Card firstCard = null;
        var turn = starter;

        for (var seat = 0; seat < PLAYERS; seat++) {
            final var client = connected.get(turn);
            final var hand = remainingHands.get(turn);
            final var card = LegalMoves.fromHand(hand, TRUMP, Optional.ofNullable(firstCard));

            client.playCard(table.gameId(), card);
            hand.remove(card);
            played.add(turn + ":" + card.suit() + "-" + card.rank());
            if (firstCard == null) {
                firstCard = card;
            }

            final var confirmations = client.awaitMessages("CARD_PLAYED", seat + 1, TIMEOUT);
            final var latest = StompTestClient.payloadOf(confirmations.get(seat));
            assertThat(latest.get("playerId")).isEqualTo(turn);
            turn = (String) latest.get("nextTurn");
        }
        return played;
    }

    private void awaitEverywhere(Map<String, StompTestClient> connected, String type, int count) {
        connected.values().forEach(client -> client.awaitMessages(type, count, TIMEOUT));
    }

    private String playerWhoGoes(Map<String, StompTestClient> connected) {
        return (String) connected.values().iterator().next().round().get("playerWhoGoes");
    }

    private static int countOccurrences(String text, String token) {
        var count = 0;
        var index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String cardOf(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        final var card = (Map<String, String>) payload.get("card");
        return card.get("suit") + "-" + card.get("rank");
    }
}
