package com.ginebra.identity.application;

import com.ginebra.identity.adapter.out.InMemorySessionStore;
import com.ginebra.identity.adapter.out.JwtProperties;
import com.ginebra.identity.adapter.out.JwtTokenAdapter;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.port.in.CreateAnonymousUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousAuthServiceTest {

    private AnonymousAuthService authService;
    private InMemorySessionStore sessionStore;

    @BeforeEach
    void setUp() {
        final var jwtProperties = new JwtProperties(
            "test-secret-key-minimum-32-characters-for-testing-purposes",
            1,
            "test-issuer"
        );
        final var tokenGenerator = new JwtTokenAdapter(jwtProperties);
        sessionStore = new InMemorySessionStore();
        authService = new AnonymousAuthService(tokenGenerator, sessionStore);
    }

    @Test
    void shouldCreateAnonymousPlayerWithGeneratedDisplayName() {
        // Arrange
        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(Optional.empty());

        // Act
        final var response = authService.createAnonymous(command);

        // Assert
        assertThat(response.token()).isNotBlank();
        assertThat(response.playerId()).isNotBlank();
        assertThat(response.displayName()).startsWith("Guest_");
        assertThat(response.displayName()).hasSize(10); // "Guest_" + 4 chars
    }

    @Test
    void shouldCreateAnonymousPlayerWithProvidedDisplayName() {
        // Arrange
        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(Optional.of("CustomPlayer"));

        // Act
        final var response = authService.createAnonymous(command);

        // Assert
        assertThat(response.token()).isNotBlank();
        assertThat(response.playerId()).isNotBlank();
        assertThat(response.displayName()).isEqualTo("CustomPlayer");
    }

    @Test
    void shouldGenerateUniquePlayerIds() {
        // Arrange
        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(Optional.empty());

        // Act
        final var response1 = authService.createAnonymous(command);
        final var response2 = authService.createAnonymous(command);

        // Assert
        assertThat(response1.playerId()).isNotEqualTo(response2.playerId());
        assertThat(response1.token()).isNotEqualTo(response2.token());
    }

    @Test
    void shouldPersistSessionInRepository() {
        // Arrange
        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(Optional.of("TestPlayer"));

        // Act
        final var response = authService.createAnonymous(command);

        // Assert
        assertThat(sessionStore.size()).isEqualTo(1);

        final var playerId = new PlayerId(java.util.UUID.fromString(response.playerId()));
        final var savedIdentity = sessionStore.findById(playerId);

        assertThat(savedIdentity).isPresent();
        assertThat(savedIdentity.get().displayName()).isEqualTo("TestPlayer");
        assertThat(savedIdentity.get().anonymous()).isTrue();
    }

    @Test
    void shouldGenerateValidJwtToken() {
        // Arrange
        final var command = new CreateAnonymousUseCase.CreateAnonymousCommand(Optional.of("TestPlayer"));

        // Act
        final var response = authService.createAnonymous(command);

        // Assert
        final var token = response.token();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT format: header.payload.signature
    }
}
