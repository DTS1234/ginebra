package com.ginebra.lobby.adapter.in;

import com.ginebra.identity.adapter.out.InMemorySessionStore;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemorySessionStore sessionStore;

    @BeforeEach
    void setUp() {
        roomRepository.clear();
        sessionStore.clear();
    }

    @Test
    void shouldCreateRoomWithAuthenticatedPlayer() {
        // Arrange - create authenticated player and get token
        final var authResponse = restTemplate.postForEntity(
            "/api/auth/anonymous",
            new HttpEntity<>(null, jsonHeaders()),
            Map.class
        );
        final var token = (String) authResponse.getBody().get("token");

        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Act - create room
        final var response = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, headers),
            Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("roomId");
        assertThat(response.getBody().get("status")).isEqualTo("WAITING");
        assertThat(response.getBody()).containsKey("players");
    }

    @Test
    void shouldReturn401WithoutAuthentication() {
        // Act - try to create room without token
        final var response = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, jsonHeaders()),
            Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WithInvalidToken() {
        // Arrange - use invalid token
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("invalid-token");

        // Act - try to create room with invalid token
        final var response = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, headers),
            Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldIncludeCreatorInResponse() {
        // Arrange - create player
        final var authResponse = restTemplate.postForEntity(
            "/api/auth/anonymous",
            new HttpEntity<>(Map.of("displayName", "TestPlayer"), jsonHeaders()),
            Map.class
        );
        final var token = (String) authResponse.getBody().get("token");

        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Act - create room
        final var response = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, headers),
            Map.class
        );

        // Assert
        assertThat(response.getBody()).containsKey("players");
        final var players = (java.util.List<?>) response.getBody().get("players");
        assertThat(players).hasSize(1);

        final var firstPlayer = (Map<?, ?>) players.get(0);
        assertThat(firstPlayer.get("displayName")).isEqualTo("TestPlayer");
    }

    @Test
    void shouldAllowPlayerToCreateMultipleRooms() {
        // Arrange - create player
        final var authResponse = restTemplate.postForEntity(
            "/api/auth/anonymous",
            new HttpEntity<>(null, jsonHeaders()),
            Map.class
        );
        final var token = (String) authResponse.getBody().get("token");

        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Act - create two rooms with same player
        final var response1 = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, headers),
            Map.class
        );
        final var response2 = restTemplate.postForEntity(
            "/api/rooms",
            new HttpEntity<>(null, headers),
            Map.class
        );

        // Assert - both succeed (no one-room-per-player limit yet)
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody().get("roomId")).isNotEqualTo(response2.getBody().get("roomId"));
    }

    private HttpHeaders jsonHeaders() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
