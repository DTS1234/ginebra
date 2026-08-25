package com.ginebra.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Drives the public REST lobby flow (anonymous auth, room create/join) so end-to-end
 * tests reach a started game the same way a real client would.
 */
public final class LobbyFixture {

    private final TestRestTemplate restTemplate;

    public LobbyFixture(TestRestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
    }

    /**
     * Creates an anonymous identity and returns its id and JWT.
     */
    public Player createAnonymousPlayer(String displayName) {
        final var body = Map.of("displayName", displayName);
        final var response = restTemplate.postForEntity(
            "/api/auth/anonymous",
            new HttpEntity<>(body, jsonHeaders()),
            Map.class
        );
        final var payload = requireBody(response.getBody(), "POST /api/auth/anonymous");
        return new Player(
            (String) payload.get("playerId"),
            (String) payload.get("displayName"),
            (String) payload.get("token")
        );
    }

    public String createRoom(String token) {
        final var response = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, bearerHeaders(token)),
            Map.class
        );
        final var payload = requireBody(response.getBody(), "POST /api/rooms");
        return (String) payload.get("roomId");
    }

    /**
     * Joins a room. Returns the game id when this join filled the room, otherwise null.
     */
    public String joinRoom(String roomId, String token) {
        final var response = restTemplate.postForEntity(
            "/api/rooms/" + roomId + "/join",
            new HttpEntity<>(null, bearerHeaders(token)),
            Map.class
        );
        final var payload = requireBody(response.getBody(), "POST /api/rooms/{id}/join");
        return (String) payload.get("gameId");
    }

    /**
     * Creates five anonymous players, fills one room with them and returns the started game.
     */
    public Table seatFivePlayers() {
        final var players = new ArrayList<Player>();
        for (var seat = 0; seat < 5; seat++) {
            players.add(createAnonymousPlayer("Player " + seat));
        }

        final var roomId = createRoom(players.get(0).token());

        String gameId = null;
        for (var seat = 1; seat < 5; seat++) {
            final var startedGameId = joinRoom(roomId, players.get(seat).token());
            if (startedGameId != null) {
                gameId = startedGameId;
            }
        }

        if (gameId == null) {
            throw new AssertionError("Room did not start a game after five players joined");
        }
        return new Table(List.copyOf(players), roomId, gameId);
    }

    private static Map<?, ?> requireBody(Map<?, ?> body, String request) {
        if (body == null) {
            throw new AssertionError("No response body from " + request);
        }
        return body;
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

    public record Player(String id, String displayName, String token) {}

    public record Table(List<Player> players, String roomId, String gameId) {

        public Player byId(String playerId) {
            return players.stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unknown player at this table: " + playerId));
        }
    }
}
