package com.ginebra.shared.config;

import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.out.TokenParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtChannelInterceptorTest {

    private TestTokenParser tokenParser;
    private JwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenParser = new TestTokenParser();
        interceptor = new JwtChannelInterceptor(tokenParser);
    }

    @Test
    void shouldAuthenticateValidTokenOnConnect() {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var identity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "valid.jwt.token";
        tokenParser.addValidToken(token, identity);

        final var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("token", token);
        accessor.setLeaveMutable(true);
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act
        final var result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(PlayerAuthentication.class);

        final var auth = (PlayerAuthentication) accessor.getUser();
        assertThat(auth.getPlayerIdentity()).isEqualTo(identity);
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldRejectMissingTokenOnConnect() {
        // Arrange
        final var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining("Missing authentication token");
    }

    @Test
    void shouldRejectBlankTokenOnConnect() {
        // Arrange
        final var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("token", "   ");
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining("Missing authentication token");
    }

    @Test
    void shouldRejectInvalidTokenOnConnect() {
        // Arrange
        final var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("token", "invalid.token");
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining("Invalid authentication token");
    }

    @Test
    void shouldPassThroughNonConnectCommands() {
        // Arrange
        final var accessor = StompHeaderAccessor.create(StompCommand.SEND);
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act
        final var result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isSameAs(message);
        assertThat(tokenParser.parseAttempts).isEmpty();
    }

    @Test
    void shouldPassThroughSubscribeCommands() {
        // Arrange
        final var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/game/123");
        final var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act
        final var result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isSameAs(message);
        assertThat(tokenParser.parseAttempts).isEmpty();
    }

    @Test
    void shouldRequireNonNullTokenParser() {
        assertThatThrownBy(() -> new JwtChannelInterceptor(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tokenParser must not be null");
    }

    // Test double - in-memory TokenParser
    private static class TestTokenParser implements TokenParser {

        private final Map<String, PlayerIdentity> validTokens = new HashMap<>();
        private final List<String> parseAttempts = new ArrayList<>();

        void addValidToken(String token, PlayerIdentity identity) {
            validTokens.put(token, identity);
        }

        @Override
        public Optional<PlayerIdentity> parseToken(String token) {
            parseAttempts.add(token);
            return Optional.ofNullable(validTokens.get(token));
        }
    }
}
