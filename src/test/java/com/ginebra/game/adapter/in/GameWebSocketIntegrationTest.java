package com.ginebra.game.adapter.in;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.identity.adapter.out.InMemorySessionStore;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketIntegrationTest {

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

    @BeforeEach
    void setUp() {
        roomRepository.clear();
        sessionStore.clear();
        gameRepository.clear();
    }

    @Test
    void shouldReceiveGameStateOnSubscribe() throws Exception {
        // Arrange: create 5 players and start a game
        final var tokens = new String[5];
        for (var i = 0; i < 5; i++) {
            final var auth = restTemplate.postForEntity(
                "/api/auth/anonymous",
                new HttpEntity<>(null, jsonHeaders()),
                Map.class
            );
            tokens[i] = (String) auth.getBody().get("token");
        }

        // Player 0 creates room
        final var createResponse = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, bearerHeaders(tokens[0])),
            Map.class
        );
        final var roomId = (String) createResponse.getBody().get("roomId");

        // Players 1-4 join room; the 5th player triggers game start
        String gameId = null;
        for (var i = 1; i <= 4; i++) {
            final var joinResponse = restTemplate.postForEntity(
                "/api/rooms/" + roomId + "/join",
                new HttpEntity<>(null, bearerHeaders(tokens[i])),
                Map.class
            );
            final var gid = (String) joinResponse.getBody().get("gameId");
            if (gid != null) {
                gameId = gid;
            }
        }
        assertThat(gameId).as("gameId should be set once room fills up").isNotNull();

        // Act: player 0 connects via WebSocket and subscribes to the game topic
        final var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        final var received = new LinkedBlockingQueue<Map>();
        final var connectHeaders = new StompHeaders();
        connectHeaders.add("token", tokens[0]);

        final var session = stompClient.connectAsync(
            "ws://localhost:" + port + "/ws/game",
            new WebSocketHttpHeaders(),
            connectHeaders,
            new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession s, StompCommand cmd, StompHeaders h, byte[] payload, Throwable ex) {
                    // swallow for test
                }

                @Override
                public void handleTransportError(StompSession s, Throwable ex) {
                    // swallow for test
                }
            }
        ).get(5, TimeUnit.SECONDS);

        // Subscribe to user-specific game state queue (sent on subscribe to game topic)
        session.subscribe("/user/queue/game-state", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((Map) payload);
            }
        });

        // Subscribe to the game topic (this triggers GameSubscriptionInterceptor to send GAME_STATE)
        session.subscribe("/topic/game/" + gameId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((Map) payload);
            }
        });

        // Assert: GAME_STATE message received within 5 seconds
        final var message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("Should receive GAME_STATE within 5 seconds").isNotNull();
        assertThat(message.get("type")).isEqualTo("GAME_STATE");

        session.disconnect();
        stompClient.stop();
    }

    private HttpHeaders jsonHeaders() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearerHeaders(String token) {
        final var headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
