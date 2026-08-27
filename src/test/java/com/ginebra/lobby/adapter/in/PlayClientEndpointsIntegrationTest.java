package com.ginebra.lobby.adapter.in;

import com.ginebra.game.adapter.out.InMemoryGameRepository;
import com.ginebra.identity.adapter.out.InMemorySessionStore;
import com.ginebra.lobby.adapter.out.InMemoryRoomRepository;
import com.ginebra.support.LobbyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The HTTP surface the static play client depends on: the page must load without a token,
 * and a player who did not fill the room must be able to find out that it became a game.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Endpoints the play client relies on")
class PlayClientEndpointsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemorySessionStore sessionStore;

    @Autowired
    private InMemoryGameRepository gameRepository;

    private LobbyFixture lobby;

    @BeforeEach
    void setUp() {
        roomRepository.clear();
        sessionStore.clear();
        gameRepository.clear();
        lobby = new LobbyFixture(restTemplate);
    }

    @Test
    void shouldServeThePlayPageWithoutAuthentication() {
        // Act
        final var page = restTemplate.getForEntity("/", String.class);

        // Assert
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Ginebra").contains("/app.js").contains("/cards.js");
    }

    @Test
    void shouldServeTheClientAssetsWithoutAuthentication() {
        // Act & Assert
        assertThat(restTemplate.getForEntity("/app.js", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/cards.js", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/style.css", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/favicon.svg", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldStillRequireAuthenticationForTheApi() {
        // Act
        final var response = restTemplate.getForEntity("/api/rooms", String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldTellAnEarlyJoinerTheRoomHasBecomeAGame() {
        // Arrange: the creator joined first, so the join call never handed them a game id
        final var table = lobby.seatFivePlayers();
        final var creator = table.players().get(0);

        // Act
        final var response = getRoom(table.roomId(), creator.token());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("gameId")).isEqualTo(table.gameId());
        assertThat(response.getBody().get("status")).isEqualTo("CONVERTED");
        assertThat((java.util.List<?>) response.getBody().get("players")).hasSize(5);
    }

    @Test
    void shouldReportNoGameIdWhileTheRoomIsStillFilling() {
        // Arrange
        final var creator = lobby.createAnonymousPlayer("Creator");
        final var roomId = lobby.createRoom(creator.token());

        // Act
        final var response = getRoom(roomId, creator.token());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("WAITING");
        assertThat(response.getBody().get("gameId")).isNull();
    }

    @Test
    void shouldHideARoomFromPlayersWhoAreNotInIt() {
        // Arrange
        final var creator = lobby.createAnonymousPlayer("Creator");
        final var roomId = lobby.createRoom(creator.token());
        final var stranger = lobby.createAnonymousPlayer("Stranger");

        // Act
        final var response = getRoom(roomId, stranger.token());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private org.springframework.http.ResponseEntity<Map<String, Object>> getRoom(String roomId, String token) {
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return (org.springframework.http.ResponseEntity<Map<String, Object>>) (org.springframework.http.ResponseEntity<?>)
            restTemplate.exchange(
                "/api/rooms/" + roomId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );
    }
}
