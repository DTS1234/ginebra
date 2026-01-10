package com.ginebra.identity.application;

import com.ginebra.identity.adapter.out.JwtProperties;
import com.ginebra.identity.adapter.out.JwtTokenAdapter;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityServiceTest {

    private IdentityService identityService;
    private JwtTokenAdapter tokenAdapter;

    @BeforeEach
    void setUp() {
        final var properties = new JwtProperties(
            "test-secret-key-minimum-32-characters-for-testing-purposes",
            1,
            "test-issuer"
        );
        tokenAdapter = new JwtTokenAdapter(properties);
        identityService = new IdentityService(tokenAdapter);
    }

    @Test
    void shouldReturnIdentityForValidToken() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));
        final var token = tokenAdapter.generateToken(identity);

        // Act
        final var response = identityService.getCurrentPlayer(token);

        // Assert
        assertThat(response.playerId()).isEqualTo(playerId.toString());
        assertThat(response.displayName()).isEqualTo("TestPlayer");
        assertThat(response.anonymous()).isTrue();
    }

    @Test
    void shouldThrowInvalidTokenExceptionForInvalidToken() {
        // Arrange
        final var invalidToken = "invalid.token.here";

        // Act & Assert
        assertThatThrownBy(() -> identityService.getCurrentPlayer(invalidToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessage("Invalid or expired token");
    }

    @Test
    void shouldThrowInvalidTokenExceptionForNullToken() {
        // Act & Assert
        assertThatThrownBy(() -> identityService.getCurrentPlayer(null))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessage("Token must not be blank");
    }

    @Test
    void shouldThrowInvalidTokenExceptionForBlankToken() {
        // Act & Assert
        assertThatThrownBy(() -> identityService.getCurrentPlayer("   "))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessage("Token must not be blank");
    }
}
