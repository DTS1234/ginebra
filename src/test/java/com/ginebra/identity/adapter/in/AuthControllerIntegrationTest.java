package com.ginebra.identity.adapter.in;

import com.ginebra.identity.adapter.out.InMemorySessionStore;
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
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemorySessionStore sessionStore;

    @BeforeEach
    void setUp() {
        sessionStore.clear();
    }

    @Test
    void shouldCreateAnonymousPlayerWithoutDisplayName() {
        // Arrange
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var request = new HttpEntity<>(null, headers);

        // Act
        final var response = restTemplate.postForEntity(
            "/api/auth/anonymous",
            request,
            Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("token")).asString().isNotBlank();
        assertThat(body.get("playerId")).asString().isNotBlank();
        assertThat(body.get("displayName")).asString().startsWith("Guest_");

        assertThat(sessionStore.size()).isEqualTo(1);
    }

    @Test
    void shouldCreateAnonymousPlayerWithDisplayName() {
        // Arrange
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var requestBody = Map.of("displayName", "TestPlayer");
        final var request = new HttpEntity<>(requestBody, headers);

        // Act
        final var response = restTemplate.postForEntity(
            "/api/auth/anonymous",
            request,
            Map.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("displayName")).isEqualTo("TestPlayer");

        assertThat(sessionStore.size()).isEqualTo(1);
    }
}
