package com.ginebra.support;

import com.ginebra.game.domain.model.Card;
import com.ginebra.game.domain.model.Rank;
import com.ginebra.game.domain.model.Suit;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A single game client speaking STOMP over a real WebSocket, for end-to-end tests.
 *
 * Every frame the client receives - on the game topic and on its two private user
 * queues - is appended to one history. Assertions read that history by message type,
 * so a test can check what a given player saw without caring about frame ordering
 * across subscriptions.
 */
public final class StompTestClient implements AutoCloseable {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final String name;
    private final String token;
    private final WebSocketStompClient stompClient;
    private final StompSession session;
    private final List<Map<String, Object>> received = Collections.synchronizedList(new ArrayList<>());

    private StompTestClient(String name, String token, WebSocketStompClient stompClient, StompSession session) {
        this.name = name;
        this.token = token;
        this.stompClient = stompClient;
        this.session = session;
    }

    /**
     * Opens a WebSocket connection and authenticates with the player's JWT.
     */
    public static StompTestClient connect(String name, int port, String token, Duration timeout) throws Exception {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(token, "token must not be null");

        final var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        final var connectHeaders = new StompHeaders();
        connectHeaders.add("token", token);

        final var session = stompClient.connectAsync(
            "ws://localhost:" + port + "/ws/game",
            new WebSocketHttpHeaders(),
            connectHeaders,
            new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession s, StompCommand cmd, StompHeaders h, byte[] p, Throwable ex) {
                    // Surfaced through missing messages; assertions report the real failure.
                }

                @Override
                public void handleTransportError(StompSession s, Throwable ex) {
                    // Same as above.
                }
            }
        ).get(timeout.toMillis(), TimeUnit.MILLISECONDS);

        return new StompTestClient(name, token, stompClient, session);
    }

    public String name() {
        return name;
    }

    public String token() {
        return token;
    }

    /**
     * Subscribes to the private queues first, then to the game topic.
     *
     * Order matters: subscribing to the topic is what makes the server push this
     * player's personalised GAME_STATE onto the private queue.
     */
    public void joinGame(String gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        subscribe("/user/queue/game-state");
        subscribe("/user/queue/errors");
        subscribe("/topic/game/" + gameId);
    }

    public void passSoledad(String gameId) {
        session.send("/app/game/" + gameId + "/soledad-pass", Map.of());
    }

    public void declareSoledad(String gameId) {
        session.send("/app/game/" + gameId + "/declare-soledad", Map.of());
    }

    public void selectTrump(String gameId, Suit suit) {
        session.send("/app/game/" + gameId + "/select-trump", Map.of("suit", suit.name()));
    }

    public void playCard(String gameId, Card card) {
        session.send(
            "/app/game/" + gameId + "/play-card",
            Map.of("card", Map.of("suit", card.suit().name(), "rank", card.rank().name()))
        );
    }

    /**
     * Waits until this client has received at least {@code count} messages of the given
     * type and returns them, oldest first.
     */
    public List<Map<String, Object>> awaitMessages(String type, int count, Duration timeout) {
        final var deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            final var matches = messagesOfType(type);
            if (matches.size() >= count) {
                return matches.subList(0, count);
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError(
                    name + " expected " + count + " '" + type + "' message(s) within " + timeout
                        + " but saw " + matches.size() + ". Received types: " + receivedTypes()
                );
            }
            sleep();
        }
    }

    public Map<String, Object> awaitMessage(String type, Duration timeout) {
        return awaitMessages(type, 1, timeout).get(0);
    }

    /**
     * Every message of the given type received so far, oldest first.
     */
    public List<Map<String, Object>> messagesOfType(String type) {
        synchronized (received) {
            return received.stream()
                .filter(message -> type.equals(message.get("type")))
                .toList();
        }
    }

    public List<String> receivedTypes() {
        synchronized (received) {
            return received.stream().map(message -> String.valueOf(message.get("type"))).toList();
        }
    }

    /**
     * The player's own hand, taken from the most recent GAME_STATE message.
     */
    public List<Card> hand() {
        final var states = messagesOfType("GAME_STATE");
        if (states.isEmpty()) {
            throw new AssertionError(name + " has not received a GAME_STATE message");
        }
        final var round = currentRound(states.get(states.size() - 1));
        @SuppressWarnings("unchecked")
        final var cards = (List<Map<String, String>>) round.get("yourHand");
        return cards.stream().map(StompTestClient::toCard).toList();
    }

    /**
     * The round payload of the most recent GAME_STATE message.
     */
    public Map<String, Object> round() {
        final var states = messagesOfType("GAME_STATE");
        if (states.isEmpty()) {
            throw new AssertionError(name + " has not received a GAME_STATE message");
        }
        return currentRound(states.get(states.size() - 1));
    }

    public static Card toCard(Map<String, String> card) {
        return new Card(Suit.valueOf(card.get("suit")), Rank.valueOf(card.get("rank")));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> payloadOf(Map<String, Object> message) {
        return (Map<String, Object>) message.get("payload");
    }

    @Override
    public void close() {
        try {
            session.disconnect();
        } catch (RuntimeException ignored) {
            // Already closed.
        }
        stompClient.stop();
    }

    private void subscribe(String destination) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new LinkedHashMap<>((Map<String, Object>) payload));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> currentRound(Map<String, Object> gameState) {
        final var payload = payloadOf(gameState);
        return (Map<String, Object>) payload.get("currentRound");
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a STOMP message", e);
        }
    }
}
