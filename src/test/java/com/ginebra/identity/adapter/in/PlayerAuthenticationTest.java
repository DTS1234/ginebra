package com.ginebra.identity.adapter.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerAuthenticationTest {

    @Test
    void shouldCreateAuthenticatedInstance() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";

        // Act
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Assert
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPlayerIdentity()).isEqualTo(playerIdentity);
        assertThat(authentication.getCredentials()).isEqualTo(token);
    }

    @Test
    void shouldReturnPlayerIdentityAsPrincipal() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act
        final var principal = authentication.getPrincipal();

        // Assert
        assertThat(principal).isEqualTo(playerIdentity);
    }

    @Test
    void shouldReturnDisplayNameAsName() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act
        final var name = authentication.getName();

        // Assert
        assertThat(name).isEqualTo("TestPlayer");
    }

    @Test
    void shouldReturnEmptyAuthorities() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act
        final var authorities = authentication.getAuthorities();

        // Assert
        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldReturnNullForDetails() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act
        final var details = authentication.getDetails();

        // Assert
        assertThat(details).isNull();
    }

    @Test
    void shouldThrowExceptionWhenSettingAuthenticatedToTrue() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act & Assert
        assertThatThrownBy(() -> authentication.setAuthenticated(true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot set authenticated to true");
    }

    @Test
    void shouldAllowSettingAuthenticatedToFalse() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "test.jwt.token";
        final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);

        // Act
        authentication.setAuthenticated(false);

        // Assert - no exception thrown
        assertThat(authentication.isAuthenticated()).isTrue(); // immutable, doesn't actually change
    }

    @Test
    void shouldRequireNonNullPlayerIdentity() {
        // Arrange
        final var token = "test.jwt.token";

        // Act & Assert
        assertThatThrownBy(() -> PlayerAuthentication.authenticated(null, token))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("playerIdentity must not be null");
    }

    @Test
    void shouldRequireNonNullToken() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);

        // Act & Assert
        assertThatThrownBy(() -> PlayerAuthentication.authenticated(playerIdentity, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("token must not be null");
    }
}
