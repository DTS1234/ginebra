package com.ginebra.identity.adapter.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextHelperTest {

    @AfterEach
    void tearDown() {
        // Clear SecurityContext after each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnPlayerIdentityWhenAuthenticated() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act
        final var result = SecurityContextHelper.getCurrentPlayerIdentity();

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(playerIdentity);
    }

    @Test
    void shouldReturnEmptyWhenNotAuthenticated() {
        // Arrange - no authentication set

        // Act
        final var result = SecurityContextHelper.getCurrentPlayerIdentity();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAuthenticationIsNotPlayerAuthentication() {
        // Arrange - set non-PlayerAuthentication
        final var mockAuthentication = new org.springframework.security.authentication.TestingAuthenticationToken(
            "principal", "credentials"
        );
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);

        // Act
        final var result = SecurityContextHelper.getCurrentPlayerIdentity();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRequirePlayerIdentityWhenAuthenticated() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act
        final var result = SecurityContextHelper.requireCurrentPlayerIdentity();

        // Assert
        assertThat(result).isEqualTo(playerIdentity);
    }

    @Test
    void shouldThrowExceptionWhenRequireCalledWithoutAuthentication() {
        // Arrange - no authentication set

        // Act & Assert
        assertThatThrownBy(() -> SecurityContextHelper.requireCurrentPlayerIdentity())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated player in SecurityContext");
    }

    @Test
    void shouldThrowExceptionWhenRequireCalledWithWrongAuthenticationType() {
        // Arrange - set non-PlayerAuthentication
        final var mockAuthentication = new org.springframework.security.authentication.TestingAuthenticationToken(
            "principal", "credentials"
        );
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);

        // Act & Assert
        assertThatThrownBy(() -> SecurityContextHelper.requireCurrentPlayerIdentity())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated player in SecurityContext");
    }
}
