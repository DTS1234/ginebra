package com.ginebra.shared.config;

import com.ginebra.identity.adapter.in.PlayerAuthentication;
import com.ginebra.identity.port.out.TokenParser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Intercepts STOMP CONNECT frames to authenticate players via JWT.
 * Clients must send their JWT token as a native STOMP header named "token".
 *
 * Example STOMP CONNECT:
 *   CONNECT
 *   token:eyJhbGciOi...
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final TokenParser tokenParser;

    public JwtChannelInterceptor(TokenParser tokenParser) {
        this.tokenParser = Objects.requireNonNull(tokenParser, "tokenParser must not be null");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        final var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            final var token = accessor.getFirstNativeHeader("token");
            if (token == null || token.isBlank()) {
                throw new MessageDeliveryException("Missing authentication token");
            }

            final var identity = tokenParser.parseToken(token)
                .orElseThrow(() -> new MessageDeliveryException("Invalid authentication token"));

            final var authentication = PlayerAuthentication.authenticated(identity, token);
            accessor.setUser(authentication);
        }

        return message;
    }
}
